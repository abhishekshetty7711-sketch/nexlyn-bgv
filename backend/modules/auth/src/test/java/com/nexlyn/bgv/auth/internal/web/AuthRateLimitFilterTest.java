package com.nexlyn.bgv.auth.internal.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nexlyn.bgv.auth.internal.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            new RateLimitService(new AuthProperties(null,
                    new AuthProperties.RateLimit(2, Duration.ofMinutes(1), 10, Duration.ofMinutes(15)), null)),
            new ObjectMapper());

    private MockHttpServletResponse call(String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void blocksTheThirdRequestFromOneIpWith429AndRetryAfter() throws Exception {
        assertThat(call("/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call("/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = call("/api/auth/login", "10.0.0.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isBetween(1L, 60L);
        assertThat(blocked.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void otherIpsAreNotAffected() throws Exception {
        call("/api/auth/login", "10.0.0.1");
        call("/api/auth/login", "10.0.0.1");
        call("/api/auth/login", "10.0.0.1");
        assertThat(call("/api/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitPathsOutsideApiAuth() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("/api/cases", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }
}
