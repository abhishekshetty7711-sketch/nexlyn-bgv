package com.nexlyn.bgv.auth.internal.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexlyn.bgv.auth.internal.service.RateLimitService;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Per-IP rate limit on every {@code /api/auth/**} request. The per-email limit lives in
 * {@code AuthService} because the email is inside the request body.
 *
 * <p>Uses the direct connection address. Behind the production reverse proxy the real client
 * address must be forwarded and trusted (Phase 8), otherwise every request looks like the proxy.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/auth/";

    private final RateLimitService rateLimit;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(RateLimitService rateLimit, ObjectMapper objectMapper) {
        this.rateLimit = rateLimit;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitService.Decision decision = rateLimit.tryConsumeIp(request.getRemoteAddr());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, (decision.retryAfter().toMillis() + 999) / 1000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiError.of(ErrorCode.RATE_LIMITED, "Too many requests. Please try again later."));
    }
}
