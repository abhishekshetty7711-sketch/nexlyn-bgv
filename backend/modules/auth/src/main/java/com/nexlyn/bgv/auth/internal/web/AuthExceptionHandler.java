package com.nexlyn.bgv.auth.internal.web;

import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Turns bad input into the standard {@link ApiError} body. Never echoes the rejected value
 * (it could be a password) and never leaks internals.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidFields(MethodArgumentNotValidException e) {
        List<ApiError.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), "is invalid"))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(ErrorCode.VALIDATION_FAILED.name(), "The request is not valid.", fields, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ErrorCode.VALIDATION_FAILED, "The request body is missing or malformed."));
    }
}
