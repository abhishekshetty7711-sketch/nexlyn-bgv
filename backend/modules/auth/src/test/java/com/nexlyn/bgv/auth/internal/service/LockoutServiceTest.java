package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LockoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final LockoutService lockout = new LockoutService(new AuthProperties(
            new AuthProperties.Lockout(5, Duration.ofMinutes(15), Duration.ofHours(4)), null, null));

    private static Admin admin() {
        return Admin.create("a@b.co", "A", "hash", NOW);
    }

    @Test
    void lockDurationsDoubleAndAreCapped() {
        assertThat(lockout.lockDuration(1)).isEqualTo(Duration.ofMinutes(15));
        assertThat(lockout.lockDuration(2)).isEqualTo(Duration.ofMinutes(30));
        assertThat(lockout.lockDuration(3)).isEqualTo(Duration.ofMinutes(60));
        assertThat(lockout.lockDuration(5)).isEqualTo(Duration.ofHours(4));
        assertThat(lockout.lockDuration(50)).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void fourFailuresDoNotLockButTheFifthDoes() {
        Admin admin = admin();
        for (int i = 0; i < 4; i++) {
            assertThat(lockout.recordFailure(admin, NOW)).isFalse();
        }
        assertThat(lockout.isLocked(admin, NOW)).isFalse();

        assertThat(lockout.recordFailure(admin, NOW)).isTrue();
        assertThat(lockout.isLocked(admin, NOW)).isTrue();
        assertThat(admin.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(admin.getFailedAttempts()).isZero();
    }

    @Test
    void lockExpiresByItself() {
        Admin admin = admin();
        for (int i = 0; i < 5; i++) {
            lockout.recordFailure(admin, NOW);
        }
        assertThat(lockout.isLocked(admin, NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(lockout.isLocked(admin, NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void theSecondLockIsLongerThanTheFirst() {
        Admin admin = admin();
        for (int i = 0; i < 5; i++) {
            lockout.recordFailure(admin, NOW);
        }
        Instant later = NOW.plus(Duration.ofMinutes(20));
        for (int i = 0; i < 5; i++) {
            lockout.recordFailure(admin, later);
        }
        assertThat(admin.getLockoutCount()).isEqualTo(2);
        assertThat(admin.getLockedUntil()).isEqualTo(later.plus(Duration.ofMinutes(30)));
    }

    @Test
    void successResetsEverything() {
        Admin admin = admin();
        for (int i = 0; i < 5; i++) {
            lockout.recordFailure(admin, NOW);
        }
        lockout.recordSuccess(admin);
        assertThat(admin.getFailedAttempts()).isZero();
        assertThat(admin.getLockoutCount()).isZero();
        assertThat(admin.getLockedUntil()).isNull();
    }
}
