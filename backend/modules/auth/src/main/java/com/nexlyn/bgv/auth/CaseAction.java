package com.nexlyn.bgv.auth;

/** What an admin wants to do with a case; each maps to one permission in {@link CaseAccessPolicy}. */
public enum CaseAction {
    READ,
    UPDATE,
    DELETE,
    ASSIGN,
    UPDATE_CHECK,
    UPLOAD_DOCUMENT,
    DELETE_DOCUMENT,
    GENERATE_REPORT,
    SUBMIT_FOR_REVIEW,
    APPROVE,
    FINALIZE
}
