package com.nexlyn.bgv.common.enums;

/** The six statuses a verification check can have (CLAUDE.md {@literal §6.2}). */
public enum CheckStatus {
    VERIFIED("Verified"),
    DISCREPANCY("Discrepancy"),
    UNABLE_TO_VERIFY("Unable to Verify"),
    CLOSED("Closed"),
    PENDING("Pending"),
    IN_PROGRESS("In Progress");

    private final String label;

    CheckStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True once the check has an outcome, whichever it is (everything except Pending and In Progress). */
    public boolean isConcluded() {
        return this != PENDING && this != IN_PROGRESS;
    }
}
