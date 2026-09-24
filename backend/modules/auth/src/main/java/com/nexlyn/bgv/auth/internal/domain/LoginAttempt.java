package com.nexlyn.bgv.auth.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One row per password check, kept for forensics and the audit trail. Never stores the password. */
@Entity
@Table(schema = "auth", name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    private String ip;

    @Column(nullable = false)
    private boolean success;

    private String reason;

    @Column(name = "at", nullable = false)
    private Instant at;

    protected LoginAttempt() {
    }

    public LoginAttempt(String email, String ip, boolean success, String reason, Instant at) {
        this.email = email;
        this.ip = ip;
        this.success = success;
        this.reason = reason;
        this.at = at;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }
}
