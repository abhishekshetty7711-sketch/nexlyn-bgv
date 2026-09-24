package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nexlyn.bgv.auth.internal.domain.LoginAttempt;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import com.nexlyn.bgv.auth.internal.repository.LoginAttemptRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The per-email rate limit must stop a guessing attack before any password work or account lookup. */
class AuthServiceRateLimitTest {

    private final AdminRepository admins = mock(AdminRepository.class);
    private final LoginAttemptRepository attempts = mock(LoginAttemptRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);

    private final AuthProperties properties = new AuthProperties(
            new AuthProperties.Lockout(5, Duration.ofMinutes(15), Duration.ofHours(4)),
            new AuthProperties.RateLimit(30, Duration.ofMinutes(1), 2, Duration.ofMinutes(15)),
            null);

    private final AuthService auth = new AuthService(admins, attempts, hasher,
            new LockoutService(properties), new RateLimitService(properties),
            mock(ApplicationEventPublisher.class), Clock.systemUTC());

    @Test
    void thirdAttemptForTheSameEmailIsRateLimitedWithoutTouchingTheAccountOrThePassword() {
        ClientInfo client = new ClientInfo("10.0.0.9", "JUnit");
        auth.verifyPassword("someone@example.com", "x", client);
        auth.verifyPassword("SOMEONE@example.com", "x", client);

        LoginOutcome third = auth.verifyPassword(" someone@example.com", "x", client);

        assertThat(third).isInstanceOfSatisfying(LoginOutcome.RateLimited.class,
                limited -> assertThat(limited.retryAfter()).isPositive());
        // Only the two earlier attempts looked the account up and burned a password check.
        verify(admins, org.mockito.Mockito.times(2)).findByEmailIgnoreCase(any());
        verify(hasher, org.mockito.Mockito.times(2)).verifyDummy(any());

        ArgumentCaptor<LoginAttempt> saved = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(attempts, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getValue().getReason()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void otherEmailsAreUnaffected() {
        ClientInfo client = new ClientInfo("10.0.0.9", "JUnit");
        auth.verifyPassword("a@example.com", "x", client);
        auth.verifyPassword("a@example.com", "x", client);
        auth.verifyPassword("a@example.com", "x", client);

        assertThat(auth.verifyPassword("b@example.com", "x", client)).isInstanceOf(LoginOutcome.InvalidCredentials.class);
    }
}
