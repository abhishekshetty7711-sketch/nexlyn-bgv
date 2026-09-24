package com.nexlyn.bgv.auth.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An invitation to become an admin. Only the hash of the link token is stored; it works once. */
@Entity
@Table(schema = "auth", name = "invitations")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "role_ids", nullable = false, columnDefinition = "uuid[]")
    private List<UUID> roleIds;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Invitation() {
    }

    public Invitation(String email, List<UUID> roleIds, String tokenHash, Instant expiresAt, UUID invitedBy, Instant now) {
        this.email = email;
        this.roleIds = List.copyOf(roleIds);
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.invitedBy = invitedBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public List<UUID> getRoleIds() {
        return roleIds;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isUsable(Instant now) {
        return acceptedAt == null && expiresAt.isAfter(now);
    }

    public void markAccepted(Instant now) {
        this.acceptedAt = now;
    }

    public void expire(Instant now) {
        this.expiresAt = now;
    }
}
