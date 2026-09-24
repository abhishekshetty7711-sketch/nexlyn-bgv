package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.domain.AdminStatus;
import com.nexlyn.bgv.auth.internal.domain.LoginAttempt;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import com.nexlyn.bgv.auth.internal.repository.LoginAttemptRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Step one of login: check the password. Handles lockout, per-email rate limiting, the
 * login-attempt record and audit events. Issuing the 2FA challenge and tokens comes after this.
 *
 * <p>The lockout counters are reset here on a correct password. The 2FA step must apply its own
 * failure counting so a stolen password does not give unlimited code guesses.
 */
@Service
public class AuthService {

    private final AdminRepository admins;
    private final LoginAttemptRepository attempts;
    private final PasswordHasher hasher;
    private final LockoutService lockout;
    private final RateLimitService rateLimit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AuthService(AdminRepository admins, LoginAttemptRepository attempts, PasswordHasher hasher,
                       LockoutService lockout, RateLimitService rateLimit,
                       ApplicationEventPublisher events, Clock clock) {
        this.admins = admins;
        this.attempts = attempts;
        this.hasher = hasher;
        this.lockout = lockout;
        this.rateLimit = rateLimit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public LoginOutcome verifyPassword(String rawEmail, String rawPassword, ClientInfo client) {
        String email = Admin.normalizeEmail(rawEmail);
        Instant now = Instant.now(clock);

        RateLimitService.Decision decision = rateLimit.tryConsumeEmail(email);
        if (!decision.allowed()) {
            record(email, client, false, "RATE_LIMITED", now);
            return new LoginOutcome.RateLimited(decision.retryAfter());
        }

        Optional<Admin> found = admins.findByEmailIgnoreCase(email);
        if (found.isEmpty()) {
            hasher.verifyDummy(rawPassword);
            record(email, client, false, "UNKNOWN_EMAIL", now);
            publishFailure(null, email, client, "UNKNOWN_EMAIL");
            return new LoginOutcome.InvalidCredentials();
        }
        Admin admin = found.get();

        if (admin.getStatus() == AdminStatus.DISABLED) {
            hasher.verifyDummy(rawPassword);
            record(email, client, false, "DISABLED", now);
            publishFailure(admin, email, client, "DISABLED");
            return new LoginOutcome.InvalidCredentials();
        }

        // While locked the password is not even checked, so guesses cannot be tested during a lock.
        if (lockout.isLocked(admin, now)) {
            record(email, client, false, "LOCKED", now);
            return new LoginOutcome.Locked(admin.getLockedUntil());
        }

        if (!hasher.matches(rawPassword, admin.getPasswordHash())) {
            boolean justLocked = lockout.recordFailure(admin, now);
            admins.save(admin);
            record(email, client, false, "BAD_PASSWORD", now);
            publishFailure(admin, email, client, "BAD_PASSWORD");
            if (justLocked) {
                events.publishEvent(AuditEvent.forAdmin("ACCOUNT_LOCKED", admin.getId(), admin.getEmail(),
                        client.ip(), client.userAgent()));
                return new LoginOutcome.Locked(admin.getLockedUntil());
            }
            return new LoginOutcome.InvalidCredentials();
        }

        lockout.recordSuccess(admin);
        admins.save(admin);
        record(email, client, true, "PASSWORD_OK", now);
        return new LoginOutcome.PasswordVerified(admin.getId(), admin.isMfaEnabled());
    }

    private void record(String email, ClientInfo client, boolean success, String reason, Instant now) {
        attempts.save(new LoginAttempt(email, client.ip(), success, reason, now));
    }

    private void publishFailure(Admin admin, String email, ClientInfo client, String reason) {
        // The reason goes in the action so the audit trail is searchable; never the password.
        events.publishEvent(AuditEvent.forAdmin("LOGIN_FAILED:" + reason,
                admin == null ? null : admin.getId(), email, client.ip(), client.userAgent()));
    }
}
