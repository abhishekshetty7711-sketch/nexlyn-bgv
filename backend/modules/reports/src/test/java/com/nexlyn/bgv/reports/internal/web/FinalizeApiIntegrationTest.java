package com.nexlyn.bgv.reports.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexlyn.bgv.reports.ReportFixtures;
import com.nexlyn.bgv.reports.ReportsIntegrationTestBase;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Finalizing a report: approved cases only, never by the preparer, protected PDF, all or nothing. */
class FinalizeApiIntegrationTest extends ReportsIntegrationTestBase {

    UUID preparerId;
    UUID reviewerId;
    String preparer;
    String reviewer;
    String caseId;

    @BeforeEach
    void fixtures() throws Exception {
        UUID clientId = newClient();
        preparerId = newAdmin("preparer@example.com", "Pia Preparer");
        reviewerId = newAdmin("reviewer@example.com", "Rex Reviewer");
        preparer = tokenFor(preparerId, "preparer@example.com", "CASE_CREATE", "CASE_READ_ASSIGNED", "CASE_UPDATE", "CHECK_UPDATE",
                "REPORT_GENERATE", "REPORT_SUBMIT_FOR_REVIEW", "REPORT_FINALIZE", "REPORT_APPROVE");
        reviewer = tokenFor(reviewerId, "reviewer@example.com", "CASE_READ_ALL", "REPORT_GENERATE", "REPORT_APPROVE", "REPORT_FINALIZE",
                "REPORT_DOWNLOAD_FINAL", "CASE_ASSIGN");
        caseId = newCase(clientId, preparer).get("id").asText();
        newCheck(caseId, "EDUCATION", preparer);
        fillCandidate(caseId, caseView(caseId, preparer).get("version").asLong(), preparer);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private String url(String suffix) {
        return "/api/cases/" + caseId + suffix;
    }

    private void submitAndApprove() throws Exception {
        assertThat(status(send(post(url("/submit-review")), preparer, obj("acknowledgeWarnings", true)))).isEqualTo(200);
        assertThat(status(send(post(url("/approve")), reviewer, null))).isEqualTo(200);
    }

    /** Makes a draft as the given admin and waits for it; returns its version number. */
    private int draftBy(String token) throws Exception {
        MvcResult accepted = send(post(url("/reports")), token, obj("acknowledgeWarnings", true));
        assertThat(status(accepted)).as(accepted.getResponse().getContentAsString()).isEqualTo(202);
        String jobId = body(accepted).get("id").asText();
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode job = body(send(get(url("/reports/jobs/" + jobId)), token, null));
            if (job.get("status").asText().equals("DONE")) {
                return job.get("version").asInt();
            }
            assertThat(job.get("status").asText()).isNotEqualTo("FAILED");
            Thread.sleep(100);
        }
        throw new AssertionError("draft did not finish");
    }

    private MvcResult finalizeAs(String token, int version, Object body) throws Exception {
        return send(post(url("/reports/" + version + "/finalize")), token, body);
    }

    private String lifecycle() throws Exception {
        return caseView(caseId, superToken()).get("lifecycle").asText();
    }

    // ---- the happy path --------------------------------------------------------------------------------------------

    @Test
    void anApprovedCaseIsFinalizedWithAProtectedPdfAndBecomesFinalized() throws Exception {
        submitAndApprove();
        int draft = draftBy(reviewer);

        MvcResult finalized = finalizeAs(reviewer, draft, obj("openPassword", "open-sesame-2026"));
        assertThat(status(finalized)).as(finalized.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode version = body(finalized);
        assertThat(version.get("version").asInt()).isEqualTo(draft + 1);
        assertThat(version.get("kind").asText()).isEqualTo("FINAL");
        assertThat(version.get("encrypted").asBoolean()).isTrue();
        assertThat(version.get("finalizedAt").isNull()).isFalse();
        assertThat(lifecycle()).isEqualTo("FINALIZED");

        // the draft is untouched; the final file needs the password
        assertThat(jdbc.queryForObject("SELECT kind FROM reports.report_versions WHERE version = ?", String.class, draft)).isEqualTo("DRAFT");
        byte[] finalBytes = storage.objects.get("reports/" + caseId + "/v" + (draft + 1) + ".pdf");
        assertThatThrownBy(() -> Loader.loadPDF(finalBytes)).isInstanceOf(IOException.class);
        try (PDDocument pdf = Loader.loadPDF(finalBytes, "open-sesame-2026")) {
            assertThat(pdf.getEncryption().getLength()).isEqualTo(256);
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
        }
        String snapshot = jdbc.queryForObject("SELECT snapshot::text FROM reports.report_versions WHERE version = ?", String.class, draft + 1);
        assertThat(snapshot).contains("Asha Rao");

        JsonNode history = body(send(get(url("/history")), reviewer, null));
        JsonNode last = history.get(history.size() - 1);
        assertThat(last.get("action").asText()).isEqualTo("FINALIZE");
        assertThat(last.get("reportVersion").asInt()).isEqualTo(draft + 1);
        assertThat(auditActionsSinceStart()).contains("REPORT_FINALIZED", "CASE_FINALIZED");
    }

    @Test
    void anOpenPasswordIsOptional() throws Exception {
        submitAndApprove();
        int draft = draftBy(reviewer);
        assertThat(status(finalizeAs(reviewer, draft, null))).isEqualTo(200);
        byte[] finalBytes = storage.objects.get("reports/" + caseId + "/v" + (draft + 1) + ".pdf");
        try (PDDocument pdf = Loader.loadPDF(finalBytes)) {
            assertThat(pdf.isEncrypted()).as("still protected against changes").isTrue();
            assertThat(pdf.getCurrentAccessPermission().canModify()).isFalse();
        }
    }

    @Test
    void aWeakOrHugeOpenPasswordIsRefusedAndNothingChanges() throws Exception {
        submitAndApprove();
        int draft = draftBy(reviewer);
        assertThat(status(finalizeAs(reviewer, draft, obj("openPassword", "short")))).isEqualTo(400);
        assertThat(status(finalizeAs(reviewer, draft, obj("openPassword", "x".repeat(200))))).isEqualTo(400);
        assertThat(lifecycle()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports.report_versions WHERE kind = 'FINAL'", Integer.class)).isZero();
    }

    // ---- when it is not allowed ---------------------------------------------------------------------------------------

    @Test
    void onlyAnApprovedCaseCanBeFinalized() throws Exception {
        int draft = draftBy(reviewer);
        MvcResult early = finalizeAs(reviewer, draft, null);
        assertThat(status(early)).isEqualTo(409);
        assertThat(body(early).get("message").asText()).contains("Only an approved case");

        assertThat(status(send(post(url("/submit-review")), preparer, obj("acknowledgeWarnings", true)))).isEqualTo(200);
        assertThat(status(finalizeAs(reviewer, draft, null))).as("in review").isEqualTo(409);
        assertThat(lifecycle()).isEqualTo("IN_REVIEW");
    }

    @Test
    void theReportMustHaveBeenMadeAfterTheApproval() throws Exception {
        int early = draftBy(preparer); // made while still in draft
        submitAndApprove();
        MvcResult stale = finalizeAs(reviewer, early, null);
        assertThat(status(stale)).isEqualTo(409);
        assertThat(body(stale).get("message").asText()).contains("before the case was approved").contains("fresh draft");
        assertThat(lifecycle()).isEqualTo("APPROVED");

        int fresh = draftBy(reviewer);
        assertThat(status(finalizeAs(reviewer, fresh, null))).isEqualTo(200);
    }

    @Test
    void thePreparerCannotFinalizeWhateverTheirPermissions() throws Exception {
        submitAndApprove();
        int draft = draftBy(reviewer);
        MvcResult own = finalizeAs(preparer, draft, null);
        assertThat(status(own)).isEqualTo(403);
        assertThat(body(own).get("message").asText()).contains("prepared or submitted").contains("finalize");

        assertThat(lifecycle()).isEqualTo("APPROVED");
        assertThat(status(finalizeAs(reviewer, draft, null))).as("the reviewer can").isEqualTo(200);
    }

    @Test
    void aSecondFinalizeIsRefused() throws Exception {
        submitAndApprove();
        int draft = draftBy(reviewer);
        assertThat(status(finalizeAs(reviewer, draft, null))).isEqualTo(200);
        assertThat(status(finalizeAs(reviewer, draft, null))).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports.report_versions WHERE kind = 'FINAL'", Integer.class)).isEqualTo(1);
    }

    @Test
    void aFinalVersionCannotBeFinalizedAgainAndUnknownVersionsAreNotFound() throws Exception {
        submitAndApprove();
        int draft = draftBy(reviewer);
        assertThat(status(finalizeAs(reviewer, draft + 7, null))).isEqualTo(404);
        assertThat(status(finalizeAs(reviewer, draft, null))).isEqualTo(200);
        // reopen and try to finalize the old FINAL as if it were a draft
        assertThat(status(send(post(url("/reopen")), reviewer, obj("reason", "Fix a typo")))).isEqualTo(200);
        submitAndApprove();
        MvcResult again = finalizeAs(reviewer, draft + 1, null);
        assertThat(status(again)).isEqualTo(409);
    }

    @Test
    void finalizingNeedsThePermissionAndAccessToTheCase() throws Exception {
        submitAndApprove();
        int draft = draftBy(reviewer);
        assertThat(status(finalizeAs(null, draft, null))).isEqualTo(401);
        String noFinalize = tokenFor(reviewerId, "reviewer@example.com", "CASE_READ_ALL", "REPORT_APPROVE");
        assertThat(status(finalizeAs(noFinalize, draft, null))).isEqualTo(403);
        UUID strangerId = newAdmin("stranger@example.com", "Sam Stranger");
        String stranger = tokenFor(strangerId, "stranger@example.com", "CASE_READ_ASSIGNED", "REPORT_FINALIZE");
        assertThat(status(finalizeAs(stranger, draft, null))).as("not on this case").isEqualTo(403);
        assertThat(lifecycle()).isEqualTo("APPROVED");
    }

    @Test
    void aDraftThatFailedItsIntegrityCheckIsNotFinalizedAndNothingIsLeftBehind() throws Exception {
        submitAndApprove();
        int draft = draftBy(reviewer);
        storage.objects.put("reports/" + caseId + "/v" + draft + ".pdf", ReportFixtures.blankPdf(9));
        MvcResult result = finalizeAs(reviewer, draft, null);
        assertThat(status(result)).isEqualTo(503);
        assertThat(lifecycle()).isEqualTo("APPROVED");
        assertThat(storage.objects.keySet()).noneMatch(key -> key.endsWith("v" + (draft + 1) + ".pdf"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports.report_versions WHERE kind = 'FINAL'", Integer.class)).isZero();
    }

    // ---- after finalizing ------------------------------------------------------------------------------------------------

    @Test
    void aFinalizedCaseIsLockedNoMoreDraftsAndTheFinalNeedsItsOwnDownloadPermission() throws Exception {
        submitAndApprove();
        int draft = draftBy(reviewer);
        finalizeAs(reviewer, draft, null);

        MvcResult more = send(post(url("/reports")), reviewer, obj("acknowledgeWarnings", true));
        assertThat(status(more)).isEqualTo(409);
        assertThat(body(more).get("message").asText()).contains("finalized").contains("Reopen");
        long version = caseView(caseId, reviewer).get("version").asLong();
        assertThat(status(send(put(url("/candidate")), preparer, obj("version", version, "fullName", "Changed", "parentType", "FATHER", "employeeId", "E")))).isEqualTo(409);

        assertThat(status(send(get(url("/reports/" + (draft + 1) + "/download")), reviewer, null))).isEqualTo(200);
        String readerOnly = tokenFor(preparerId, "preparer@example.com", "CASE_READ_ASSIGNED");
        assertThat(status(send(get(url("/reports/" + (draft + 1) + "/download")), readerOnly, null))).as("no final-download permission").isEqualTo(403);
        assertThat(status(send(get(url("/reports/" + draft + "/download")), readerOnly, null))).as("the draft is still readable").isEqualTo(200);
    }

    @Test
    void reopeningKeepsTheFinalReportAndTheNextReportIsANewVersion() throws Exception {
        submitAndApprove();
        int draft = draftBy(reviewer);
        finalizeAs(reviewer, draft, obj("openPassword", "open-sesame-2026"));
        assertThat(status(send(post(url("/reopen")), reviewer, obj("reason", "Client asked for a correction")))).isEqualTo(200);
        assertThat(lifecycle()).isEqualTo("DRAFT");

        submitAndApprove();
        int newDraft = draftBy(reviewer);
        assertThat(newDraft).isEqualTo(draft + 2);
        JsonNode versions = body(send(get(url("/reports")), reviewer, null));
        List<String> kinds = new ArrayList<>();
        versions.forEach(v -> kinds.add(v.get("version").asInt() + ":" + v.get("kind").asText()));
        assertThat(kinds).containsExactly((draft + 2) + ":DRAFT", (draft + 1) + ":FINAL", draft + ":DRAFT");
        assertThat(status(send(get(url("/reports/" + (draft + 1) + "/download")), reviewer, null))).as("the old final stays").isEqualTo(200);
    }
}
