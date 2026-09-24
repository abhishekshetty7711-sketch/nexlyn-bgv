package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.common.error.ErrorCode;

import java.time.Duration;

/**
 * Outcome of a login-flow step. Expected failures are returned, not thrown, because throwing out
 * of a transactional method would roll back the failure counters that must be saved.
 */
public sealed interface FlowResult<T> {

    record Ok<T>(T value) implements FlowResult<T> {
    }

    /** {@code retryAfter} is null unless the client should wait (rate limit, lockout). */
    record Failure<T>(ErrorCode code, Duration retryAfter) implements FlowResult<T> {
        public static <T> Failure<T> of(ErrorCode code) {
            return new Failure<>(code, null);
        }
    }
}
