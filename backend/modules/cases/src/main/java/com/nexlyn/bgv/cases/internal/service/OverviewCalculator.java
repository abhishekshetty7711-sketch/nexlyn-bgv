package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.common.enums.CheckStatus;

import java.util.List;

/**
 * The automatic numbers of the report overview (CLAUDE.md {@literal §6.2}), worked out from the
 * checks. Each can be overridden by hand; an empty override falls back to the automatic value.
 *
 * <p>Rules (decision D-022): <b>Total</b> = number of checks. <b>Completed</b> = checks that have an
 * outcome (everything except Pending and In Progress). <b>Overall status</b>, first match wins: no
 * checks = "Pending"; any Discrepancy = "Discrepancy"; any Unable to Verify = "Unable to Verify";
 * any Pending or In Progress = "In Progress"; all Closed = "Closed"; otherwise "Clear" (the word the reference tool prints, D-032).
 */
public final class OverviewCalculator {

    public record Overview(int total, int completed, String overallStatus) {
    }

    private OverviewCalculator() {
    }

    public static Overview auto(List<CheckStatus> statuses) {
        int total = statuses.size();
        int completed = (int) statuses.stream().filter(CheckStatus::isConcluded).count();
        return new Overview(total, completed, overallStatus(statuses));
    }

    /** The automatic values with any manual overrides applied (null or blank override = use the automatic value). */
    public static Overview effective(Overview auto, Integer totalOverride, Integer completedOverride, String statusOverride) {
        return new Overview(
                totalOverride != null ? totalOverride : auto.total(),
                completedOverride != null ? completedOverride : auto.completed(),
                statusOverride != null && !statusOverride.isBlank() ? statusOverride : auto.overallStatus());
    }

    private static String overallStatus(List<CheckStatus> statuses) {
        if (statuses.isEmpty()) {
            return "Pending";
        }
        if (statuses.contains(CheckStatus.DISCREPANCY)) {
            return CheckStatus.DISCREPANCY.label();
        }
        if (statuses.contains(CheckStatus.UNABLE_TO_VERIFY)) {
            return CheckStatus.UNABLE_TO_VERIFY.label();
        }
        if (statuses.contains(CheckStatus.PENDING) || statuses.contains(CheckStatus.IN_PROGRESS)) {
            return CheckStatus.IN_PROGRESS.label();
        }
        if (statuses.stream().allMatch(status -> status == CheckStatus.CLOSED)) {
            return CheckStatus.CLOSED.label();
        }
        return "Clear";
    }
}
