package com.nexlyn.bgv.cases.internal.domain;

/** What an admin does on one case. The preparer of a case can never approve it (maker-checker, Phase 7). */
public enum CaseRole {
    PREPARER,
    REVIEWER
}
