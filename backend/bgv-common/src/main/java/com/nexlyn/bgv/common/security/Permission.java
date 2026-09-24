package com.nexlyn.bgv.common.security;

/**
 * Every permission code (CLAUDE.md {@literal §11.2}). Code checks permissions, never role names.
 * The names must match the seeded {@code auth.permissions} rows exactly; a test enforces that.
 * Use them in {@code @PreAuthorize("hasAuthority('CASE_CREATE')")}.
 */
public enum Permission {
    CASE_CREATE,
    CASE_READ_ALL,
    CASE_READ_ASSIGNED,
    CASE_UPDATE,
    CASE_DELETE,
    CASE_ASSIGN,
    CHECK_UPDATE,
    DOCUMENT_UPLOAD,
    DOCUMENT_DELETE,
    PII_UNMASK,
    REPORT_GENERATE,
    REPORT_SUBMIT_FOR_REVIEW,
    REPORT_APPROVE,
    REPORT_FINALIZE,
    REPORT_DOWNLOAD_FINAL,
    ATTESTATION_APPLY,
    CLIENT_MANAGE,
    SETTINGS_MANAGE,
    USER_MANAGE,
    ROLE_MANAGE,
    AUDIT_READ
}
