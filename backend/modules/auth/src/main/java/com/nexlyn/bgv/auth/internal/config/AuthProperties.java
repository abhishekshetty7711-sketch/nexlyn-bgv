package com.nexlyn.bgv.auth.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/** Tunable auth settings ({@code nexlyn.auth.*}). Defaults follow CLAUDE.md {@literal §11.4}. */
@ConfigurationProperties(prefix = "nexlyn.auth")
public record AuthProperties(
        @DefaultValue Lockout lockout,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Bootstrap bootstrap,
        @DefaultValue Jwt jwt,
        @DefaultValue Session session,
        @DefaultValue Cookie cookie,
        @DefaultValue Totp totp) {

    /** Convenience for code and tests that only care about the first three groups. */
    public AuthProperties(Lockout lockout, RateLimit rateLimit, Bootstrap bootstrap) {
        this(lockout, rateLimit, bootstrap, null, null, null, null);
    }

    @ConstructorBinding
    public AuthProperties {
        if (jwt == null) {
            jwt = new Jwt(null, null, null, "nexlyn-bgv", Duration.ofMinutes(15), Duration.ofMinutes(5), List.of());
        } else if (jwt.previousKeys() == null) {
            jwt = new Jwt(jwt.privateKey(), jwt.publicKey(), jwt.keyId(), jwt.issuer(), jwt.accessTtl(),
                    jwt.challengeTtl(), List.of());
        }
        if (session == null) {
            session = new Session(Duration.ofMinutes(30), Duration.ofHours(12));
        }
        if (cookie == null) {
            cookie = new Cookie(true);
        }
        if (totp == null) {
            totp = new Totp(null, "Nexlyn BGV");
        }
    }

    /** Lock after N consecutive failures; each further lock doubles, up to a cap. */
    public record Lockout(
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15m") Duration baseDuration,
            @DefaultValue("4h") Duration maxDuration) {
    }

    /** Per-IP limit on every /api/auth/** request, per-email limit on login and 2FA attempts. */
    public record RateLimit(
            @DefaultValue("30") int ipRequests,
            @DefaultValue("1m") Duration ipWindow,
            @DefaultValue("10") int emailAttempts,
            @DefaultValue("15m") Duration emailWindow) {
    }

    /** First-start super admin. Both blank = do nothing. Read from env only, never logged. */
    public record Bootstrap(String email, String password) {
    }

    /**
     * RS256 signing keys as PEM text (real or {@code \n}-escaped newlines). Blank outside prod
     * means a temporary key pair is generated at startup; prod refuses to start without keys.
     * {@code previousKeys} keep old tokens verifiable during key rotation.
     */
    public record Jwt(
            String privateKey,
            String publicKey,
            String keyId,
            @DefaultValue("nexlyn-bgv") String issuer,
            @DefaultValue("15m") Duration accessTtl,
            @DefaultValue("5m") Duration challengeTtl,
            @DefaultValue List<PreviousKey> previousKeys) {
    }

    public record PreviousKey(String keyId, String publicKey) {
    }

    /** Idle timeout is the refresh-token lifetime; the absolute limit caps a whole login session. */
    public record Session(
            @DefaultValue("30m") Duration idleTimeout,
            @DefaultValue("12h") Duration absoluteTimeout) {
    }

    /** {@code secure=false} only for plain-http local development. */
    public record Cookie(@DefaultValue("true") boolean secure) {
    }

    /** {@code encryptionKey}: Base64 of 32 bytes. Blank outside prod = temporary key. */
    public record Totp(String encryptionKey, @DefaultValue("Nexlyn BGV") String issuer) {
    }
}
