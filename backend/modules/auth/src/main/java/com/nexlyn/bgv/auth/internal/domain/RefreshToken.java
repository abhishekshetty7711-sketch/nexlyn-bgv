package com.nexlyn.bgv.auth.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One refresh token in a login session. {@code familyId} is the session id (the {@code sid}
 * claim). Only a hash of the token is stored; each use rotates it into a new row.
 */
@Entity
@Table(schema = "auth", name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "family_started_at", nullable = false)
    private Instant familyStartedAt;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    protected RefreshToken() {
    }

    public RefreshToken(UUID adminId, UUID familyId, String tokenHash, Instant familyStartedAt,
                        Instant issuedAt, Instant expiresAt, String ip, String userAgent) {
        this.adminId = adminId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.familyStartedAt = familyStartedAt;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.ip = ip;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getFamilyStartedAt() {
        return familyStartedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }

    /** Marks this token as used up; {@code replacement} is the token issued in its place. */
    public void rotateTo(UUID replacement, Instant now) {
        this.revokedAt = now;
        this.replacedBy = replacement;
    }
}
