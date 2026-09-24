package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private final RateLimitService limiter = new RateLimitService(new AuthProperties(null,
            new AuthProperties.RateLimit(3, Duration.ofMinutes(1), 2, Duration.ofMinutes(15)), null));

    @Test
    void allowsUpToTheLimitPerIpThenBlocksWithARetryTime() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsumeIp("10.0.0.1").allowed()).isTrue();
        }
        RateLimitService.Decision blocked = limiter.tryConsumeIp("10.0.0.1");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void ipsAreLimitedIndependently() {
        for (int i = 0; i < 3; i++) {
            limiter.tryConsumeIp("10.0.0.1");
        }
        assertThat(limiter.tryConsumeIp("10.0.0.1").allowed()).isFalse();
        assertThat(limiter.tryConsumeIp("10.0.0.2").allowed()).isTrue();
    }

    @Test
    void emailLimitIgnoresCaseAndSpaces() {
        assertThat(limiter.tryConsumeEmail("Owner@Example.com").allowed()).isTrue();
        assertThat(limiter.tryConsumeEmail(" owner@example.com ").allowed()).isTrue();
        assertThat(limiter.tryConsumeEmail("OWNER@EXAMPLE.COM").allowed()).isFalse();
        assertThat(limiter.tryConsumeEmail("someone.else@example.com").allowed()).isTrue();
    }
}
