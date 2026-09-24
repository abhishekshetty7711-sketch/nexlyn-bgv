package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Actions an admin performs on their own account. */
@Service
public class AccountService {

    private final AdminRepository admins;
    private final PasswordHasher hasher;
    private final PasswordPolicyService policy;
    private final LockoutService lockout;
    private final RateLimitService rateLimit;
    private final SessionService sessions;
    private final AuthApi auth;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountService(AdminRepository admins, PasswordHasher hasher, PasswordPolicyService policy,
                          LockoutService lockout, RateLimitService rateLimit, SessionService sessions, AuthApi auth,
                          ApplicationEventPublisher events, Clock clock) {
        this.admins = admins;
        this.hasher = hasher;
        this.policy = policy;
        this.lockout = lockout;
        this.rateLimit = rateLimit;
        this.sessions = sessions;
        this.auth = auth;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Changes the password after re-checking the current one (a stolen access token alone is not
     * enough). A wrong current password counts towards lockout, so this cannot be used to guess it.
     * All sessions end afterwards, including this one: the admin signs in again with the new password.
     *
     * <p>{@code noRollbackFor}: the failure counters must be saved even though an exception is thrown.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(noRollbackFor = ApiException.class)
    public void changePassword(String currentPassword, String newPassword) {
        AdminPrincipal caller = auth.requireCurrentAdmin();
        Admin admin = admins.findById(caller.id())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required."));
        Instant now = Instant.now(clock);

        RateLimitService.Decision decision = rateLimit.tryConsumeEmail("pw:" + admin.getEmail());
        if (!decision.allowed()) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "Too many attempts. Please try again later.", List.of(), decision.retryAfter());
        }
        if (lockout.isLocked(admin, now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "This account is temporarily locked. Try again later.",
                    List.of(), Duration.between(now, admin.getLockedUntil()));
        }
        if (!hasher.matches(currentPassword, admin.getPasswordHash())) {
            boolean justLocked = lockout.recordFailure(admin, now);
            admins.save(admin);
            events.publishEvent(AuditEvent.forAdmin("PASSWORD_CHANGE_FAILED", admin.getId(), admin.getEmail(), null, null));
            if (justLocked) {
                events.publishEvent(AuditEvent.forAdmin("ACCOUNT_LOCKED", admin.getId(), admin.getEmail(), null, null));
            }
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "The current password is not correct.");
        }
        List<PasswordPolicyService.Problem> problems = policy.check(newPassword, admin.getEmail());
        if (!problems.isEmpty()) {
            throw new ApiException(ErrorCode.WEAK_PASSWORD, "The new password does not meet the password rules.",
                    List.of(new ApiError.FieldError("newPassword", problems.toString())), null);
        }
        if (newPassword.equals(currentPassword)) {
            throw new ApiException(ErrorCode.WEAK_PASSWORD, "Choose a password you have not used just now.",
                    List.of(new ApiError.FieldError("newPassword", "[SAME_AS_CURRENT]")), null);
        }

        admin.changePassword(hasher.hash(newPassword), now);
        lockout.recordSuccess(admin);
        admins.save(admin);
        sessions.endAllSessions(admin.getId());
        events.publishEvent(AuditEvent.forAdmin("PASSWORD_CHANGED", admin.getId(), admin.getEmail(), null, null));
    }
}
