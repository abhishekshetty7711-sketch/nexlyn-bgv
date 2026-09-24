package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Account lockout (CLAUDE.md {@literal §11.4}): after N consecutive failures the account locks for
 * a base duration; every further lock doubles that, up to a cap. A successful check resets it all.
 * Only mutates the entity; the caller saves it.
 */
@Service
public class LockoutService {

    private final AuthProperties.Lockout settings;

    public LockoutService(AuthProperties properties) {
        this.settings = properties.lockout();
    }

    public boolean isLocked(Admin admin, Instant now) {
        return admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(now);
    }

    /** Duration of the n-th lock (n starts at 1): base, 2x base, 4x base ... capped. */
    Duration lockDuration(int lockNumber) {
        Duration duration = settings.baseDuration();
        for (int i = 1; i < lockNumber; i++) {
            duration = duration.multipliedBy(2);
            if (duration.compareTo(settings.maxDuration()) >= 0) {
                return settings.maxDuration();
            }
        }
        return duration.compareTo(settings.maxDuration()) > 0 ? settings.maxDuration() : duration;
    }

    /** Counts a failed check. Returns true if this failure just locked the account. */
    public boolean recordFailure(Admin admin, Instant now) {
        int failures = admin.getFailedAttempts() + 1;
        if (failures < settings.maxFailedAttempts()) {
            admin.setFailedAttempts(failures);
            return false;
        }
        int lockNumber = admin.getLockoutCount() + 1;
        admin.setFailedAttempts(0);
        admin.setLockoutCount(lockNumber);
        admin.setLockedUntil(now.plus(lockDuration(lockNumber)));
        return true;
    }

    public void recordSuccess(Admin admin) {
        admin.setFailedAttempts(0);
        admin.setLockoutCount(0);
        admin.setLockedUntil(null);
    }
}
