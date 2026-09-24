package com.nexlyn.bgv.common.error;

/** Stable, machine-readable error codes. Add values as features need them. */
public enum ErrorCode {
    VALIDATION_FAILED,
    RATE_LIMITED,
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    INVALID_CHALLENGE,
    INVALID_CODE,
    INVALID_REFRESH_TOKEN,
    CSRF_FAILED
}
