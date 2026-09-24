package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.domain.AdminStatus;
import com.nexlyn.bgv.auth.internal.domain.Role;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import com.nexlyn.bgv.auth.internal.security.JwtService;
import com.nexlyn.bgv.auth.internal.security.JwtService.ChallengeClaims;
import com.nexlyn.bgv.auth.internal.security.JwtService.ChallengePurpose;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The whole login journey (CLAUDE.md {@literal §9.1}):
 * <ol>
 *   <li>{@link #login}: password, then a short-lived challenge token</li>
 *   <li>{@link #beginSetup}/{@link #confirmSetup} (first login) or {@link #verifyTwoFactor}: second factor, then tokens</li>
 *   <li>{@link #refresh}, {@link #logout}</li>
 * </ol>
 * Lockout counters are only reset by a completed login, and wrong second-factor codes count as failures.
 */
@Service
public class LoginFlowService {

    public static final String STATUS_2FA_REQUIRED = "2FA_REQUIRED";
    public static final String STATUS_2FA_SETUP_REQUIRED = "2FA_SETUP_REQUIRED";

    public record Challenge(String status, String challengeToken, long expiresInSeconds) {
    }

    /** Everything the controller needs to answer a successful login or refresh. */
    public record Tokens(String accessToken, long accessExpiresInSeconds, String refreshToken,
                         Instant refreshExpiresAt, String csrfToken, List<String> backupCodes) {
    }

    private final AuthService auth;
    private final AdminRepository admins;
    private final JwtService jwt;
    private final TwoFactorService twoFactor;
    private final SessionService sessions;
    private final LockoutService lockout;
    private final RateLimitService rateLimit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public LoginFlowService(AuthService auth, AdminRepository admins, JwtService jwt, TwoFactorService twoFactor,
                            SessionService sessions, LockoutService lockout, RateLimitService rateLimit,
                            ApplicationEventPublisher events, Clock clock) {
        this.auth = auth;
        this.admins = admins;
        this.jwt = jwt;
        this.twoFactor = twoFactor;
        this.sessions = sessions;
        this.lockout = lockout;
        this.rateLimit = rateLimit;
        this.events = events;
        this.clock = clock;
    }

    // ---- step 1: password --------------------------------------------------------------

    public FlowResult<Challenge> login(String email, String password, ClientInfo client) {
        LoginOutcome outcome = auth.verifyPassword(email, password, client);
        Instant now = Instant.now(clock);
        return switch (outcome) {
            case LoginOutcome.PasswordVerified ok -> {
                ChallengePurpose purpose = ok.mfaEnabled() ? ChallengePurpose.VERIFY : ChallengePurpose.SETUP;
                yield new FlowResult.Ok<>(new Challenge(
                        ok.mfaEnabled() ? STATUS_2FA_REQUIRED : STATUS_2FA_SETUP_REQUIRED,
                        jwt.issueChallenge(ok.adminId(), purpose),
                        jwt.challengeTtl().toSeconds()));
            }
            case LoginOutcome.InvalidCredentials ignored -> FlowResult.Failure.of(ErrorCode.INVALID_CREDENTIALS);
            case LoginOutcome.Locked locked ->
                    new FlowResult.Failure<>(ErrorCode.ACCOUNT_LOCKED, Duration.between(now, locked.until()));
            case LoginOutcome.RateLimited limited ->
                    new FlowResult.Failure<>(ErrorCode.RATE_LIMITED, limited.retryAfter());
        };
    }

    // ---- step 2a: first-time 2FA enrolment ---------------------------------------------

    @Transactional
    public FlowResult<TwoFactorService.SetupInfo> beginSetup(String challengeToken) {
        Optional<Admin> admin = resolveChallenge(challengeToken, ChallengePurpose.SETUP);
        if (admin.isEmpty() || admin.get().isMfaEnabled()) {
            return FlowResult.Failure.of(ErrorCode.INVALID_CHALLENGE);
        }
        return new FlowResult.Ok<>(twoFactor.beginSetup(admin.get()));
    }

    /** Confirms enrolment with the first code; success also completes the login and returns backup codes. */
    @Transactional
    public FlowResult<Tokens> confirmSetup(String challengeToken, String code, ClientInfo client) {
        Optional<Admin> found = resolveChallenge(challengeToken, ChallengePurpose.SETUP);
        if (found.isEmpty() || found.get().isMfaEnabled()) {
            return FlowResult.Failure.of(ErrorCode.INVALID_CHALLENGE);
        }
        Admin admin = found.get();
        Instant now = Instant.now(clock);
        FlowResult.Failure<Tokens> blocked = gate(admin, now);
        if (blocked != null) {
            return blocked;
        }
        Optional<List<String>> backupCodes = twoFactor.confirmSetup(admin, code);
        if (backupCodes.isEmpty()) {
            return codeRejected(admin, client, now);
        }
        events.publishEvent(AuditEvent.forAdmin("TWO_FACTOR_ENABLED", admin.getId(), admin.getEmail(),
                client.ip(), client.userAgent()));
        return new FlowResult.Ok<>(completeLogin(admin, client, now, backupCodes.get()));
    }

    // ---- step 2b: 2FA on later logins ---------------------------------------------------

    @Transactional
    public FlowResult<Tokens> verifyTwoFactor(String challengeToken, String code, ClientInfo client) {
        Optional<Admin> found = resolveChallenge(challengeToken, ChallengePurpose.VERIFY);
        if (found.isEmpty() || !found.get().isMfaEnabled()) {
            return FlowResult.Failure.of(ErrorCode.INVALID_CHALLENGE);
        }
        Admin admin = found.get();
        Instant now = Instant.now(clock);
        FlowResult.Failure<Tokens> blocked = gate(admin, now);
        if (blocked != null) {
            return blocked;
        }
        TwoFactorService.CodeResult result = twoFactor.verify(admin, code);
        if (result == TwoFactorService.CodeResult.REJECTED) {
            return codeRejected(admin, client, now);
        }
        if (result == TwoFactorService.CodeResult.BACKUP_CODE_ACCEPTED) {
            events.publishEvent(AuditEvent.forAdmin("BACKUP_CODE_USED", admin.getId(), admin.getEmail(),
                    client.ip(), client.userAgent()));
        }
        return new FlowResult.Ok<>(completeLogin(admin, client, now, null));
    }

    // ---- refresh / logout ---------------------------------------------------------------

    @Transactional
    public FlowResult<Tokens> refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return FlowResult.Failure.of(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        SessionService.Rotation rotation = sessions.rotate(rawRefreshToken, client);
        return switch (rotation) {
            case SessionService.Rotation.Invalid ignored -> FlowResult.Failure.of(ErrorCode.INVALID_REFRESH_TOKEN);
            case SessionService.Rotation.ReuseDetected reuse -> {
                admins.findById(reuse.adminId()).ifPresent(admin -> events.publishEvent(AuditEvent.forAdmin(
                        "REFRESH_TOKEN_REUSE_DETECTED", admin.getId(), admin.getEmail(), client.ip(), client.userAgent())));
                yield FlowResult.Failure.of(ErrorCode.INVALID_REFRESH_TOKEN);
            }
            case SessionService.Rotation.Rotated rotated -> {
                Optional<Admin> admin = admins.findById(rotated.adminId());
                if (admin.isEmpty() || admin.get().getStatus() == AdminStatus.DISABLED) {
                    sessions.endAllSessions(rotated.adminId());
                    yield FlowResult.Failure.of(ErrorCode.INVALID_REFRESH_TOKEN);
                }
                yield new FlowResult.Ok<>(tokensFor(admin.get(), rotated.token(), null));
            }
        };
    }

    @Transactional
    public void logout(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        sessions.endSession(rawRefreshToken).flatMap(admins::findById).ifPresent(admin ->
                events.publishEvent(AuditEvent.forAdmin("LOGOUT", admin.getId(), admin.getEmail(),
                        client.ip(), client.userAgent())));
    }

    // ---- helpers ----------------------------------------------------------------------

    private Optional<Admin> resolveChallenge(String challengeToken, ChallengePurpose expected) {
        ChallengeClaims claims;
        try {
            claims = jwt.parseChallenge(challengeToken);
        } catch (JwtService.InvalidTokenException e) {
            return Optional.empty();
        }
        if (claims.purpose() != expected) {
            return Optional.empty();
        }
        return admins.findById(claims.adminId()).filter(a -> a.getStatus() != AdminStatus.DISABLED);
    }

    /** Refuses the attempt if the account is locked or the 2FA rate limit is used up. Null = go ahead. */
    private <T> FlowResult.Failure<T> gate(Admin admin, Instant now) {
        if (lockout.isLocked(admin, now)) {
            return new FlowResult.Failure<>(ErrorCode.ACCOUNT_LOCKED, Duration.between(now, admin.getLockedUntil()));
        }
        RateLimitService.Decision decision = rateLimit.tryConsumeEmail("2fa:" + admin.getEmail());
        if (!decision.allowed()) {
            return new FlowResult.Failure<>(ErrorCode.RATE_LIMITED, decision.retryAfter());
        }
        return null;
    }

    private <T> FlowResult<T> codeRejected(Admin admin, ClientInfo client, Instant now) {
        boolean justLocked = lockout.recordFailure(admin, now);
        admins.save(admin);
        events.publishEvent(AuditEvent.forAdmin("TWO_FACTOR_FAILED", admin.getId(), admin.getEmail(),
                client.ip(), client.userAgent()));
        if (justLocked) {
            events.publishEvent(AuditEvent.forAdmin("ACCOUNT_LOCKED", admin.getId(), admin.getEmail(),
                    client.ip(), client.userAgent()));
            return new FlowResult.Failure<>(ErrorCode.ACCOUNT_LOCKED, Duration.between(now, admin.getLockedUntil()));
        }
        return FlowResult.Failure.of(ErrorCode.INVALID_CODE);
    }

    private Tokens completeLogin(Admin admin, ClientInfo client, Instant now, List<String> backupCodes) {
        lockout.recordSuccess(admin);
        admin.setLastLoginAt(now);
        admins.save(admin);
        SessionService.IssuedToken refresh = sessions.startSession(admin.getId(), client);
        events.publishEvent(AuditEvent.forAdmin("LOGIN_SUCCESS", admin.getId(), admin.getEmail(),
                client.ip(), client.userAgent()));
        return tokensFor(admin, refresh, backupCodes);
    }

    private Tokens tokensFor(Admin admin, SessionService.IssuedToken refresh, List<String> backupCodes) {
        TreeSet<String> roles = new TreeSet<>();
        TreeSet<String> permissions = new TreeSet<>();
        for (Role role : admin.getRoles()) {
            roles.add(role.getCode());
            permissions.addAll(role.getPermissions());
        }
        String access = jwt.issueAccessToken(admin.getId(), admin.getEmail(), roles, permissions, refresh.familyId());
        return new Tokens(access, jwt.accessTtl().toSeconds(), refresh.rawToken(), refresh.expiresAt(),
                sessions.newCsrfToken(), backupCodes);
    }
}
