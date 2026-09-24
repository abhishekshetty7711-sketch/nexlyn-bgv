package com.nexlyn.bgv.common.web;

import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;

/** One place that decides which HTTP status goes with which {@link ErrorCode}. */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static HttpStatus status(ErrorCode code) {
        return switch (code) {
            case UNAUTHENTICATED, INVALID_CREDENTIALS, INVALID_CHALLENGE, INVALID_CODE, INVALID_REFRESH_TOKEN ->
                    HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, CSRF_FAILED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case ACCOUNT_LOCKED -> HttpStatus.LOCKED;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case VALIDATION_FAILED, WEAK_PASSWORD, INVALID_INVITATION -> HttpStatus.BAD_REQUEST;
        };
    }

    public static ResponseEntity<ApiError> response(ErrorCode code, String message, List<ApiError.FieldError> fields, Duration retryAfter) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status(code)).header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (retryAfter != null) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, (retryAfter.toMillis() + 999) / 1000)));
        }
        return builder.body(new ApiError(code.name(), message, fields, null));
    }
}
