package com.nexlyn.bgv.auth.internal.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * In-memory token-bucket rate limits (Bucket4j) for the auth endpoints, per client IP and per
 * email address. Buckets expire when idle so an attacker cannot grow memory by inventing emails.
 * In-memory is enough for the single-instance deployment in CLAUDE.md {@literal §14}.
 */
@Service
public class RateLimitService {

    /** Outcome of one attempt; {@code retryAfter} is only meaningful when not allowed. */
    public record Decision(boolean allowed, Duration retryAfter) {
        static final Decision ALLOWED = new Decision(true, Duration.ZERO);
    }

    private static final long MAX_TRACKED_KEYS = 100_000;

    private final AuthProperties.RateLimit settings;
    private final Cache<String, Bucket> ipBuckets;
    private final Cache<String, Bucket> emailBuckets;

    public RateLimitService(AuthProperties properties) {
        this.settings = properties.rateLimit();
        this.ipBuckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS).expireAfterAccess(settings.ipWindow().multipliedBy(2)).build();
        this.emailBuckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS).expireAfterAccess(settings.emailWindow().multipliedBy(2)).build();
    }

    public Decision tryConsumeIp(String ip) {
        String key = ip == null ? "unknown" : ip;
        return consume(ipBuckets, key, settings.ipRequests(), settings.ipWindow());
    }

    public Decision tryConsumeEmail(String email) {
        String key = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return consume(emailBuckets, key, settings.emailAttempts(), settings.emailWindow());
    }

    private static Decision consume(Cache<String, Bucket> cache, String key, int capacity, Duration window) {
        Bucket bucket = cache.get(key, k -> Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillIntervally(capacity, window))
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Decision.ALLOWED;
        }
        return new Decision(false, Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }
}
