package com.nexlyn.bgv.auth.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An admin's TOTP secret, AES-256-GCM encrypted. Unconfirmed until the first code is verified. */
@Entity
@Table(schema = "auth", name = "totp_secrets")
public class TotpSecret {

    @Id
    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "secret_encrypted", nullable = false)
    private String secretEncrypted;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    protected TotpSecret() {
    }

    public TotpSecret(UUID adminId, String secretEncrypted, Instant createdAt) {
        this.adminId = adminId;
        this.secretEncrypted = secretEncrypted;
        this.createdAt = createdAt;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    /** Replace an unconfirmed secret (setup restarted). */
    public void replaceSecret(String secretEncrypted, Instant now) {
        this.secretEncrypted = secretEncrypted;
        this.createdAt = now;
        this.confirmedAt = null;
        this.lastUsedStep = null;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public void confirm(Instant now) {
        this.confirmedAt = now;
    }

    public Long getLastUsedStep() {
        return lastUsedStep;
    }

    public void setLastUsedStep(Long lastUsedStep) {
        this.lastUsedStep = lastUsedStep;
    }
}
