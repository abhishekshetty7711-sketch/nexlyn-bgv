package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.cases.internal.service.OverviewCalculator.Overview;
import com.nexlyn.bgv.common.enums.CheckStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.nexlyn.bgv.common.enums.CheckStatus.CLOSED;
import static com.nexlyn.bgv.common.enums.CheckStatus.DISCREPANCY;
import static com.nexlyn.bgv.common.enums.CheckStatus.IN_PROGRESS;
import static com.nexlyn.bgv.common.enums.CheckStatus.PENDING;
import static com.nexlyn.bgv.common.enums.CheckStatus.UNABLE_TO_VERIFY;
import static com.nexlyn.bgv.common.enums.CheckStatus.VERIFIED;
import static org.assertj.core.api.Assertions.assertThat;

class OverviewCalculatorTest {

    private static Overview auto(CheckStatus... statuses) {
        return OverviewCalculator.auto(List.of(statuses));
    }

    @Test
    void noChecksMeansNothingIsDoneYet() {
        assertThat(auto()).isEqualTo(new Overview(0, 0, "Pending"));
    }

    @Test
    void totalCountsAllChecksAndCompletedCountsThoseWithAnOutcome() {
        Overview overview = auto(VERIFIED, DISCREPANCY, UNABLE_TO_VERIFY, CLOSED, PENDING, IN_PROGRESS);
        assertThat(overview.total()).isEqualTo(6);
        assertThat(overview.completed()).isEqualTo(4);
    }

    @Test
    void theWorstOutcomeWinsTheOverallStatus() {
        assertThat(auto(VERIFIED, VERIFIED).overallStatus()).isEqualTo("Completed");
        assertThat(auto(VERIFIED, CLOSED).overallStatus()).isEqualTo("Completed");
        assertThat(auto(CLOSED, CLOSED).overallStatus()).isEqualTo("Closed");
        assertThat(auto(VERIFIED, PENDING).overallStatus()).isEqualTo("In Progress");
        assertThat(auto(VERIFIED, IN_PROGRESS, CLOSED).overallStatus()).isEqualTo("In Progress");
        assertThat(auto(VERIFIED, UNABLE_TO_VERIFY, PENDING).overallStatus()).isEqualTo("Unable to Verify");
        assertThat(auto(VERIFIED, UNABLE_TO_VERIFY, DISCREPANCY, PENDING).overallStatus()).isEqualTo("Discrepancy");
    }

    @Test
    void manualOverridesReplaceTheAutomaticValuesAndEmptyOnesFallBack() {
        Overview auto = auto(VERIFIED, PENDING);
        assertThat(OverviewCalculator.effective(auto, null, null, null)).isEqualTo(auto);
        assertThat(OverviewCalculator.effective(auto, 10, 9, "Completed")).isEqualTo(new Overview(10, 9, "Completed"));
        assertThat(OverviewCalculator.effective(auto, 0, null, "  ")).isEqualTo(new Overview(0, 1, "In Progress"));
    }
}
