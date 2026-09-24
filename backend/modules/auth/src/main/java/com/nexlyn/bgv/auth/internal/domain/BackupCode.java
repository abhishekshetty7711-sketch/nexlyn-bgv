package com.nexlyn.bgv.auth.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A single-use recovery code, stored only as an Argon2id hash. */
@Entity
@Table(schema = "auth", name = "backup_codes")
public class BackupCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    protected BackupCode() {
    }

    public BackupCode(UUID adminId, String codeHash) {
        this.adminId = adminId;
        this.codeHash = codeHash;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
