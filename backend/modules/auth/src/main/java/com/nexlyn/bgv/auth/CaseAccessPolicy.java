package com.nexlyn.bgv.auth;

import java.util.UUID;

/**
 * Data-level authorization for case-scoped operations (CLAUDE.md {@literal §11.4}, layer 3). Every
 * service method that takes a case id must call {@link #check} first, so that guessing another
 * case's id gets an analyst nothing.
 *
 * <p>Rule: the admin needs the permission for the action, and unless they hold {@code CASE_READ_ALL}
 * the case must be assigned to them.
 */
public interface CaseAccessPolicy {

    /** Returns normally if allowed; otherwise throws {@code AccessDeniedException} (HTTP 403). */
    void check(UUID caseId, CaseAction action);

    /** Non-throwing form of {@link #check}. */
    boolean isAllowed(UUID caseId, CaseAction action);
}
