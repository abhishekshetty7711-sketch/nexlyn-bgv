package com.nexlyn.bgv.common.web;

import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Turns failures of every controller into the standard {@link ApiError} body. Never echoes a
 * rejected value (it could be a password or an identifier) and never leaks internals. Access-denied
 * errors are left to the security filter chain, which answers them with 401/403.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> expected(ApiException e) {
        return ApiErrors.response(e.code(), e.getMessage(), e.fieldErrors(), e.retryAfter());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidFields(MethodArgumentNotValidException e) {
        List<ApiError.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), "is invalid"))
                .toList();
        return ApiErrors.response(ErrorCode.VALIDATION_FAILED, "The request is not valid.", fields, null);
    }

    /** {@code @Valid} on a request parameter or path variable. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> invalidParameters(HandlerMethodValidationException e) {
        return ApiErrors.response(ErrorCode.VALIDATION_FAILED, "The request is not valid.", List.of(), null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> unreadable(Exception e) {
        return ApiErrors.response(ErrorCode.VALIDATION_FAILED, "The request is missing or malformed.", List.of(), null);
    }
}
