package com.nexlyn.bgv.cases.internal.web;

import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.common.web.ApiErrors;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Two admins saving the same record at the same instant: the database notices and the loser gets a
 * clear 409 to reload, instead of a server error.
 */
@RestControllerAdvice(basePackages = "com.nexlyn.bgv.cases")
class CasesExceptionHandler {

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentChange(ObjectOptimisticLockingFailureException e) {
        return ApiErrors.response(ErrorCode.CONFLICT,
                "This record was changed by someone else at the same moment. Reload it and try again.", List.of(), null);
    }
}
