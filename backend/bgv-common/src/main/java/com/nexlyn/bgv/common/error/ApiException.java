package com.nexlyn.bgv.common.error;

import java.time.Duration;
import java.util.List;

/**
 * An expected failure of an authenticated admin operation, answered with the standard error body.
 * The message is shown to the caller, so it must never contain secrets or internals.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ApiError.FieldError> fieldErrors;
    private final Duration retryAfter;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of(), null);
    }

    public ApiException(ErrorCode code, String message, List<ApiError.FieldError> fieldErrors, Duration retryAfter) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
        this.retryAfter = retryAfter;
    }

    public ErrorCode code() {
        return code;
    }

    public List<ApiError.FieldError> fieldErrors() {
        return fieldErrors;
    }

    /** Non-null when the client should wait before retrying (rate limit, lockout). */
    public Duration retryAfter() {
        return retryAfter;
    }
}
