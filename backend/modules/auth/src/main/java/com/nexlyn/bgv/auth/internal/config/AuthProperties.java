package com.nexlyn.bgv.auth.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Tunable auth settings ({@code nexlyn.auth.*}). Defaults follow CLAUDE.md {@literal §11.4}. */
@ConfigurationProperties(prefix = "nexlyn.auth")
public record AuthProperties(
        @DefaultValue Lockout lockout,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Bootstrap bootstrap) {

    /** Lock after N consecutive failures; each further lock doubles, up to a cap. */
    public record Lockout(
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15m") Duration baseDuration,
            @DefaultValue("4h") Duration maxDuration) {
    }

    /** Per-IP limit on every /api/auth/** request, per-email limit on login attempts. */
    public record RateLimit(
            @DefaultValue("30") int ipRequests,
            @DefaultValue("1m") Duration ipWindow,
            @DefaultValue("10") int emailAttempts,
            @DefaultValue("15m") Duration emailWindow) {
    }

    /** First-start super admin. Both blank = do nothing. Read from env only, never logged. */
    public record Bootstrap(String email, String password) {
    }
}
