package com.nexlyn.bgv.auth.internal.web;

import com.nexlyn.bgv.auth.internal.service.ApiException;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Turns failures of the auth controllers into the standard {@link ApiError} body. Never echoes a
 * rejected value (it could be a password) and never leaks internals. Access-denied errors are left
 * to the security filter chain, which answers them with 401/403.
 */
@RestControllerAdvice(basePackages = "com.nexlyn.bgv.auth.internal.web")
public class AuthExceptionHandler {

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

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> unreadable(Exception e) {
        return ApiErrors.response(ErrorCode.VALIDATION_FAILED, "The request is missing or malformed.", List.of(), null);
    }
}
