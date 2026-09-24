package com.nexlyn.bgv.cases.internal.domain;

/** The four status pills of the report cover (CLAUDE.md {@literal §6.2}); title and subtitle stay editable. */
public enum StatusPreset {
    COMPLETED("Completed", "All Requested Verifications Completed"),
    DISCREPANCY("Discrepancy", "Discrepancy Found in Verification"),
    UNABLE("Unable to Verify", "Unable to Complete Verification"),
    CLOSED("Closed", "Verification Closed / Insufficient Data");

    private final String title;
    private final String subtitle;

    StatusPreset(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }
}
