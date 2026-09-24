package com.nexlyn.bgv.common.enums;

/**
 * Where a case is in its life (CLAUDE.md {@literal §11.3}):
 * DRAFT to IN_REVIEW to APPROVED to FINALIZED, with CHANGES_REQUESTED sending it back to the preparer.
 */
public enum CaseLifecycle {
    DRAFT,
    IN_REVIEW,
    CHANGES_REQUESTED,
    APPROVED,
    FINALIZED;

    /** Case data can only be edited while the preparer is working on it. */
    public boolean isEditable() {
        return this == DRAFT || this == CHANGES_REQUESTED;
    }
}
