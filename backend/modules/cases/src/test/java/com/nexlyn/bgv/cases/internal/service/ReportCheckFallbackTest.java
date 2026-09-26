package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.cases.CaseReport;
import com.nexlyn.bgv.cases.internal.service.CheckViews.CheckView;
import com.nexlyn.bgv.common.enums.CheckStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** What the report prints for "This card verifies" and for the page-1 description when the admin leaves them blank. */
class ReportCheckFallbackTest {

    private static CheckView view(String summary, String verifies) {
        return new CheckView(UUID.randomUUID(), UUID.randomUUID(), "EMPLOYMENT", "Employment Verification", "Experience Letter", "employment",
                "Employment Verification", summary, verifies, CheckStatus.VERIFIED, "Standard", null, null, null,
                null, false, null, null, 0, 0, List.of(), List.of(), List.of(), Instant.EPOCH);
    }

    @Test
    void whatTheAdminTypedForThisCardVerifiesIsUsed() {
        CaseReport.Check check = CaseApiImpl.reportCheck(view("Past employers and their HR", "Experience Letter of Infosys"));
        assertThat(check.cardVerifies()).isEqualTo("Experience Letter of Infosys");
        assertThat(check.summaryDescription()).isEqualTo("Past employers and their HR");
    }

    @Test
    void aBlankThisCardVerifiesFallsBackToTheDocumentTypeNotToThePageOneDescription() {
        CaseReport.Check check = CaseApiImpl.reportCheck(view("Past employer confirmation of tenure, designation and salary", "  "));
        assertThat(check.cardVerifies()).isEqualTo("Experience Letter");
        assertThat(check.summaryDescription()).isEqualTo("Past employer confirmation of tenure, designation and salary");
    }

    @Test
    void whenBothAreBlankBothUseTheDocumentType() {
        CaseReport.Check check = CaseApiImpl.reportCheck(view(null, null));
        assertThat(check.cardVerifies()).isEqualTo("Experience Letter");
        assertThat(check.summaryDescription()).isEqualTo("Experience Letter");
        assertThat(check.documentName()).isEqualTo("Experience Letter");
    }
}
