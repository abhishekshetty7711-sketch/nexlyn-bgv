package com.nexlyn.bgv.auth.internal.web;

import com.nexlyn.bgv.auth.internal.service.ClientInfo;
import com.nexlyn.bgv.auth.internal.service.FlowResult;
import com.nexlyn.bgv.auth.internal.service.LoginFlowService;
import com.nexlyn.bgv.auth.internal.service.TwoFactorService;
import com.nexlyn.bgv.auth.internal.web.AuthDtos.ChallengeResponse;
import com.nexlyn.bgv.auth.internal.web.AuthDtos.CodeRequest;
import com.nexlyn.bgv.auth.internal.web.AuthDtos.LoginRequest;
import com.nexlyn.bgv.auth.internal.web.AuthDtos.SetupRequest;
import com.nexlyn.bgv.auth.internal.web.AuthDtos.SetupResponse;
import com.nexlyn.bgv.auth.internal.web.AuthDtos.TokenResponse;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

/** {@code /api/auth/**}: thin HTTP layer over {@link LoginFlowService}. Never logs passwords, codes or tokens. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginFlowService flow;
    private final AuthCookies cookies;
    private final Clock clock;

    public AuthController(LoginFlowService flow, AuthCookies cookies, Clock clock) {
        this.flow = flow;
        this.cookies = cookies;
        this.clock = clock;
    }

    /** Password step. Answers {@code 2FA_REQUIRED} or {@code 2FA_SETUP_REQUIRED} plus a challenge token. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return switch (flow.login(body.email(), body.password(), client(request))) {
            case FlowResult.Ok<LoginFlowService.Challenge> ok -> ResponseEntity.ok(new ChallengeResponse(
                    ok.value().status(), ok.value().challengeToken(), ok.value().expiresInSeconds()));
            case FlowResult.Failure<LoginFlowService.Challenge> failure -> error(failure);
        };
    }

    /** First login only: creates the authenticator secret and returns it (once) with the QR link. */
    @PostMapping("/2fa/setup")
    public ResponseEntity<?> setup(@Valid @RequestBody SetupRequest body) {
        return switch (flow.beginSetup(body.challengeToken())) {
            case FlowResult.Ok<TwoFactorService.SetupInfo> ok ->
                    ResponseEntity.ok(new SetupResponse(ok.value().secret(), ok.value().otpauthUri()));
            case FlowResult.Failure<TwoFactorService.SetupInfo> failure -> error(failure);
        };
    }

    /** First login only: first code from the app turns 2FA on, completes login, returns the backup codes once. */
    @PostMapping("/2fa/confirm")
    public ResponseEntity<?> confirm(@Valid @RequestBody CodeRequest body, HttpServletRequest request,
                                     HttpServletResponse response) {
        return tokens(flow.confirmSetup(body.challengeToken(), body.code(), client(request)), response);
    }

    /** Later logins: authenticator code or backup code completes login. */
    @PostMapping("/2fa/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody CodeRequest body, HttpServletRequest request,
                                    HttpServletResponse response) {
        return tokens(flow.verifyTwoFactor(body.challengeToken(), body.code(), client(request)), response);
    }

    /** Silent refresh: needs the refresh cookie and the matching CSRF header. Rotates the refresh token. */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        if (!cookies.csrfMatches(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiError.of(ErrorCode.CSRF_FAILED, "Missing or invalid CSRF token."));
        }
        FlowResult<LoginFlowService.Tokens> result = flow.refresh(cookies.refreshToken(request), client(request));
        if (result instanceof FlowResult.Failure<LoginFlowService.Tokens>) {
            cookies.clear(response);
        }
        return tokens(result, response);
    }

    /** Ends the session. Uses the refresh cookie + CSRF so it still works after the access token expired. */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        if (!cookies.csrfMatches(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiError.of(ErrorCode.CSRF_FAILED, "Missing or invalid CSRF token."));
        }
        flow.logout(cookies.refreshToken(request), client(request));
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    // ---- helpers ----------------------------------------------------------------------

    private ResponseEntity<?> tokens(FlowResult<LoginFlowService.Tokens> result, HttpServletResponse response) {
        return switch (result) {
            case FlowResult.Ok<LoginFlowService.Tokens> ok -> {
                LoginFlowService.Tokens t = ok.value();
                cookies.write(response, t.refreshToken(), t.refreshExpiresAt(), t.csrfToken(), clock.instant());
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(new TokenResponse(t.accessToken(), "Bearer", t.accessExpiresInSeconds(), t.backupCodes()));
            }
            case FlowResult.Failure<LoginFlowService.Tokens> failure -> error(failure);
        };
    }

    private static ResponseEntity<ApiError> error(FlowResult.Failure<?> failure) {
        HttpStatus status = switch (failure.code()) {
            case INVALID_CREDENTIALS, INVALID_CHALLENGE, INVALID_CODE, INVALID_REFRESH_TOKEN -> HttpStatus.UNAUTHORIZED;
            case CSRF_FAILED -> HttpStatus.FORBIDDEN;
            case ACCOUNT_LOCKED -> HttpStatus.LOCKED;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store");
        Duration wait = failure.retryAfter();
        if (wait != null) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, (wait.toMillis() + 999) / 1000)));
        }
        return builder.body(ApiError.of(failure.code(), message(failure.code())));
    }

    /** Generic on purpose: the same text for a wrong password, unknown email and disabled account. */
    private static String message(ErrorCode code) {
        return switch (code) {
            case INVALID_CREDENTIALS -> "Invalid email or password.";
            case ACCOUNT_LOCKED -> "This account is temporarily locked. Try again later.";
            case INVALID_CHALLENGE -> "Your sign-in step expired. Please sign in again.";
            case INVALID_CODE -> "That code is not valid.";
            case INVALID_REFRESH_TOKEN -> "Your session has ended. Please sign in again.";
            case CSRF_FAILED -> "Missing or invalid CSRF token.";
            case RATE_LIMITED -> "Too many requests. Please try again later.";
            case VALIDATION_FAILED -> "The request is not valid.";
        };
    }

    private static ClientInfo client(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
