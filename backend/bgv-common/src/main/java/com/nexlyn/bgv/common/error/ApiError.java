package com.nexlyn.bgv.common.error;

import java.util.List;

/**
 * Error body returned by every failing API call (CLAUDE.md {@literal §9}).
 * Never carries stack traces, SQL or other internals.
 */
public record ApiError(String code, String message, List<FieldError> fieldErrors, String correlationId) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message, List.of(), null);
    }
}
