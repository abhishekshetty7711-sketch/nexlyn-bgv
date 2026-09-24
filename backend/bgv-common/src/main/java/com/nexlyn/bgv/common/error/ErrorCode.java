package com.nexlyn.bgv.common.error;

/** Stable, machine-readable error codes. Add values as features need them. */
public enum ErrorCode {
    VALIDATION_FAILED,
    WEAK_PASSWORD,
    RATE_LIMITED,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    INVALID_CHALLENGE,
    INVALID_CODE,
    INVALID_REFRESH_TOKEN,
    INVALID_INVITATION,
    CSRF_FAILED,
    FILE_TOO_LARGE,
    UNSUPPORTED_FILE,
    SERVICE_UNAVAILABLE
}
