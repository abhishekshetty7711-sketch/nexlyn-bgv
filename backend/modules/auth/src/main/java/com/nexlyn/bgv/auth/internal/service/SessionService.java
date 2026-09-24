package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nexlyn.bgv.auth.internal.domain.RefreshToken;
import com.nexlyn.bgv.auth.internal.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Login sessions and rotating refresh tokens (CLAUDE.md {@literal §11.4}).
 *
 * <p>A session is a "family" of refresh tokens sharing a {@code familyId} (the {@code sid} claim).
 * Every refresh replaces the token with a new one. Presenting a token that was already replaced
 * means it leaked, so the whole family is revoked. A token lives for the idle timeout, and no
 * family lives longer than the absolute timeout.
 */
@Service
public class SessionService {

    /** A freshly issued refresh token. {@code rawToken} goes into the cookie and is never stored. */
    public record IssuedToken(String rawToken, UUID familyId, Instant expiresAt) {
    }

    public sealed interface Rotation {
        /** A valid token was exchanged for a new one. */
        record Rotated(UUID adminId, IssuedToken token) implements Rotation {
        }

        /** Unknown, expired, revoked, or a harmless double-submit (see {@link #REUSE_GRACE}). */
        record Invalid() implements Rotation {
        }

        /** An already-used token came back: the session family has been revoked. */
        record ReuseDetected(UUID adminId, UUID familyId) implements Rotation {
        }
    }

    /**
     * Two browser tabs can refresh at the same moment, so a token presented again within this window
     * after being replaced is refused without revoking the session. Later reuse is treated as theft.
     */
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final RefreshTokenRepository tokens;
    private final AuthProperties.Session settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SessionService(RefreshTokenRepository tokens, AuthProperties properties, Clock clock) {
        this.tokens = tokens;
        this.settings = properties.session();
        this.clock = clock;
    }

    /** Begins a new session for an admin who has just completed login. */
    @Transactional
    public IssuedToken startSession(UUID adminId, ClientInfo client) {
        Instant now = Instant.now(clock);
        return issue(adminId, UUID.randomUUID(), now, now, client).token;
    }

    @Transactional
    public Rotation rotate(String rawToken, ClientInfo client) {
        Instant now = Instant.now(clock);
        Optional<RefreshToken> found = tokens.findForUpdateByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return new Rotation.Invalid();
        }
        RefreshToken current = found.get();

        if (current.getRevokedAt() != null) {
            if (current.getReplacedBy() == null) {
                return new Rotation.Invalid(); // ended by logout or an admin, not by a refresh
            }
            if (now.isBefore(current.getRevokedAt().plus(REUSE_GRACE))) {
                return new Rotation.Invalid(); // near-simultaneous refresh, leave the session alone
            }
            tokens.revokeFamily(current.getFamilyId(), now);
            return new Rotation.ReuseDetected(current.getAdminId(), current.getFamilyId());
        }
        if (!current.getExpiresAt().isAfter(now)
                || !current.getFamilyStartedAt().plus(settings.absoluteTimeout()).isAfter(now)) {
            return new Rotation.Invalid();
        }

        Issued next = issue(current.getAdminId(), current.getFamilyId(), current.getFamilyStartedAt(), now, client);
        current.rotateTo(next.entityId, now);
        tokens.save(current);
        return new Rotation.Rotated(current.getAdminId(), next.token);
    }

    /** Ends the session that owns this token (logout). Returns the admin id if the token was known. */
    @Transactional
    public Optional<UUID> endSession(String rawToken) {
        return tokens.findForUpdateByTokenHash(hash(rawToken)).map(token -> {
            tokens.revokeFamily(token.getFamilyId(), Instant.now(clock));
            return token.getAdminId();
        });
    }

    /** Ends every session of an admin (disable, role change, password change). */
    @Transactional
    public void endAllSessions(UUID adminId) {
        tokens.revokeAllForAdmin(adminId, Instant.now(clock));
    }

    /** True while the session still has a live refresh token; access tokens for a dead session must be refused. */
    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId) {
        return tokens.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(sessionId, Instant.now(clock));
    }

    /** A random value for the CSRF double-submit cookie. */
    public String newCsrfToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Issued(IssuedToken token, UUID entityId) {
    }

    private Issued issue(UUID adminId, UUID familyId, Instant familyStartedAt, Instant now, ClientInfo client) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant idleLimit = now.plus(settings.idleTimeout());
        Instant absoluteLimit = familyStartedAt.plus(settings.absoluteTimeout());
        Instant expiresAt = idleLimit.isBefore(absoluteLimit) ? idleLimit : absoluteLimit;

        RefreshToken saved = tokens.save(new RefreshToken(adminId, familyId, hash(raw), familyStartedAt, now,
                expiresAt, client.ip(), truncate(client.userAgent())));
        return new Issued(new IssuedToken(raw, familyId, expiresAt), saved.getId());
    }

    /** SHA-256 is enough here: the token is 256 bits of randomness, so there is nothing to brute-force. */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
