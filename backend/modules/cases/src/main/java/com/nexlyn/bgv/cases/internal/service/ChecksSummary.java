package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.common.enums.CheckStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the case workspace needs to know about a case's verification checks: their statuses (for the
 * overview numbers and progress) and what is still missing (for validation).
 */
public interface ChecksSummary {

    /** One check as far as validation and the overview are concerned. */
    record CheckSummary(UUID id, String title, CheckStatus status, LocalDate requestedDate, LocalDate completedDate,
                        List<String> missingRequired) {
    }

    /** Every check of the case, in report order. */
    List<CheckSummary> summariesOf(UUID caseId);

    /** The status of each check, in report order. */
    default List<CheckStatus> statusesOf(UUID caseId) {
        return summariesOf(caseId).stream().map(CheckSummary::status).toList();
    }
}
