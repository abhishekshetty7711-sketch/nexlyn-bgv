package com.nexlyn.bgv.reports.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexlyn.bgv.reports.ReportFixtures;
import com.nexlyn.bgv.reports.ReportsIntegrationTestBase;
import com.nexlyn.bgv.reports.internal.render.BrowserLocator;
import com.nexlyn.bgv.reports.internal.service.ReportService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.awt.Color;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ReportApiIntegrationTest extends ReportsIntegrationTestBase {

    @Autowired ReportService reports;

    UUID clientId;
    UUID analystId;
    String analyst;        // prepares assigned cases and may generate reports
    String analystNoGen;   // may read but not generate
    String caseId;
    String checkId;

    @BeforeEach
    void fixtures() throws Exception {
        clientId = newClient();
        analystId = newAdmin("analyst@example.com", "Ann Analyst");
        String[] base = {"CASE_CREATE", "CASE_READ_ASSIGNED", "CASE_UPDATE", "CHECK_UPDATE", "DOCUMENT_UPLOAD", "REPORT_GENERATE"};
        analyst = tokenFor(analystId, "analyst@example.com", base);
        analystNoGen = tokenFor(analystId, "analyst@example.com", "CASE_READ_ASSIGNED");
        JsonNode created = newCase(clientId, analyst);
        caseId = created.get("id").asText();
        checkId = newCheck(caseId, "EDUCATION", analyst).get("id").asText();
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private String reportsUrl(String suffix) {
        return "/api/cases/" + caseId + "/reports" + suffix;
    }

    /** Makes the case ready: candidate filled in, and a document on the check so no warnings remain except the ones we allow. */
    private void makeCaseReady() throws Exception {
        fillCandidate(caseId, caseView(caseId, analyst).get("version").asLong(), analyst);
    }

    private JsonNode waitForJob(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode job = body(send(get(reportsUrl("/jobs/" + jobId)), analyst, null));
            String status = job.get("status").asText();
            if (status.equals("DONE") || status.equals("FAILED")) {
                return job;
            }
            Thread.sleep(150);
        }
        throw new AssertionError("the report job did not finish in time");
    }

    private JsonNode generate(boolean acknowledge) throws Exception {
        MvcResult accepted = send(post(reportsUrl("")), analyst, obj("acknowledgeWarnings", acknowledge));
        assertThat(status(accepted)).as(accepted.getResponse().getContentAsString()).isEqualTo(202);
        return waitForJob(body(accepted).get("id").asText());
    }

    // ---- preview -------------------------------------------------------------------------------------------

    @Test
    void thePreviewIsLockedDownHtmlWithTheReportInIt() throws Exception {
        makeCaseReady();
        MvcResult preview = send(get(reportsUrl("/preview")), analyst, null);
        assertThat(status(preview)).isEqualTo(200);
        assertThat(preview.getResponse().getContentType()).startsWith("text/html");
        assertThat(preview.getResponse().getHeader("Content-Security-Policy")).contains("default-src 'none'").contains("sandbox");
        assertThat(preview.getResponse().getHeader("Cache-Control")).contains("no-store");
        String html = preview.getResponse().getContentAsString();
        assertThat(html).contains("Asha Rao", "EMP-1001", "Education Verification").contains("background:#eef3f5");
        assertThat(html).contains(caseView(caseId, analyst).get("reportId").asText());
    }

    @Test
    void thePreviewWorksEvenWhileErrorsRemain() throws Exception {
        assertThat(status(send(get(reportsUrl("/preview")), analyst, null))).isEqualTo(200);
    }

    @Test
    void thePreviewNeedsSigningInAndAccessToTheCase() throws Exception {
        assertThat(status(send(get(reportsUrl("/preview")), null, null))).isEqualTo(401);
        UUID otherId = newAdmin("other@example.com", "Otto Other");
        String other = tokenFor(otherId, "other@example.com", "CASE_READ_ASSIGNED", "REPORT_GENERATE");
        assertThat(status(send(get(reportsUrl("/preview")), other, null))).as("not assigned to this case").isEqualTo(403);
        assertThat(status(send(get("/api/cases/" + UUID.randomUUID() + "/reports/preview"), superToken(), null))).isEqualTo(404);
    }

    // ---- making a draft -----------------------------------------------------------------------------------------

    @Test
    void errorsBlockTheReportAndSaySo() throws Exception {
        MvcResult blocked = send(post(reportsUrl("")), analyst, obj("acknowledgeWarnings", true));
        assertThat(status(blocked)).isEqualTo(409);
        assertThat(body(blocked).get("message").asText()).contains("cannot be generated yet").contains("full name");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports.report_jobs", Integer.class)).isZero();
    }

    @Test
    void warningsNeedAnAcknowledgement() throws Exception {
        makeCaseReady();
        MvcResult unacknowledged = send(post(reportsUrl("")), analyst, obj("acknowledgeWarnings", false));
        assertThat(status(unacknowledged)).isEqualTo(409);
        assertThat(body(unacknowledged).get("message").asText()).contains("warning").contains("confirm");
        MvcResult noBody = send(post(reportsUrl("")), analyst, null);
        assertThat(status(noBody)).as("no body means not acknowledged").isEqualTo(409);
    }

    @Test
    void aDraftIsMadeInTheBackgroundStoredAndListedAsVersionOne() throws Exception {
        makeCaseReady();
        JsonNode job = generate(true);

        assertThat(job.get("status").asText()).isEqualTo("DONE");
        assertThat(job.get("version").asInt()).isEqualTo(1);
        assertThat(job.get("error").isNull()).isTrue();

        JsonNode versions = body(send(get(reportsUrl("")), analyst, null));
        assertThat(versions).hasSize(1);
        JsonNode v1 = versions.get(0);
        assertThat(v1.get("version").asInt()).isEqualTo(1);
        assertThat(v1.get("kind").asText()).isEqualTo("DRAFT");
        assertThat(v1.get("encrypted").asBoolean()).isFalse();
        assertThat(v1.get("generatedByName").asText()).isEqualTo("Ann Analyst");
        assertThat(v1.get("pageCount").asInt()).isEqualTo(2);

        String key = jdbc.queryForObject("SELECT pdf_storage_key FROM reports.report_versions", String.class);
        assertThat(key).isEqualTo("reports/" + caseId + "/v1.pdf");
        assertThat(storage.objects).containsKey(key);
        String snapshot = jdbc.queryForObject("SELECT snapshot::text FROM reports.report_versions", String.class);
        assertThat(snapshot).contains("Asha Rao").contains(caseView(caseId, analyst).get("reportId").asText());
    }

    @Test
    void eachRunIsANewVersionAndOldOnesStayDownloadable() throws Exception {
        makeCaseReady();
        assertThat(generate(true).get("version").asInt()).isEqualTo(1);
        assertThat(generate(true).get("version").asInt()).isEqualTo(2);
        JsonNode versions = body(send(get(reportsUrl("")), analyst, null));
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).get("version").asInt()).as("newest first").isEqualTo(2);
        assertThat(status(send(get(reportsUrl("/1/download")), analyst, null))).isEqualTo(200);
        assertThat(status(send(get(reportsUrl("/2/download")), analyst, null))).isEqualTo(200);
        assertThat(status(send(get(reportsUrl("/3/download")), analyst, null))).isEqualTo(404);
    }

    @Test
    void onlyOneReportAtATimeForACase() throws Exception {
        makeCaseReady();
        renderer.delayMillis = 1500;
        MvcResult first = send(post(reportsUrl("")), analyst, obj("acknowledgeWarnings", true));
        assertThat(status(first)).isEqualTo(202);
        MvcResult second = send(post(reportsUrl("")), analyst, obj("acknowledgeWarnings", true));
        assertThat(status(second)).isEqualTo(409);
        assertThat(body(second).get("message").asText()).contains("already being made");
        assertThat(waitForJob(body(first).get("id").asText()).get("status").asText()).isEqualTo("DONE");
    }

    @Test
    void aFailedRenderEndsTheJobWithAPlainMessageAndLeaksNothing() throws Exception {
        makeCaseReady();
        renderer.mode = SwitchablePdfRenderer.Mode.FAIL;
        JsonNode job = generate(true);
        assertThat(job.get("status").asText()).isEqualTo("FAILED");
        assertThat(job.get("error").asText()).contains("could not be generated").doesNotContain("chrome").doesNotContain("secret");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports.report_versions", Integer.class)).isZero();
        assertThat(auditActionsSinceStart()).contains("REPORT_GENERATION_FAILED");
        // and the next attempt works
        renderer.mode = SwitchablePdfRenderer.Mode.FAKE;
        assertThat(generate(true).get("status").asText()).isEqualTo("DONE");
    }

    @Test
    void jobsLeftUnfinishedByAStoppedServerAreMarkedFailed() throws Exception {
        UUID stuck = jdbc.queryForObject(
                "INSERT INTO reports.report_jobs (case_id, status, requested_by) VALUES (?::uuid, 'RUNNING', ?) RETURNING id", UUID.class, caseId, analystId);
        reports.failInterruptedJobs();
        JsonNode job = body(send(get(reportsUrl("/jobs/" + stuck)), analyst, null));
        assertThat(job.get("status").asText()).isEqualTo("FAILED");
        assertThat(job.get("error").asText()).contains("restarted");
    }

    @Test
    void anUnknownJobIsNotFound() throws Exception {
        assertThat(status(send(get(reportsUrl("/jobs/" + UUID.randomUUID())), analyst, null))).isEqualTo(404);
    }

    // ---- download ---------------------------------------------------------------------------------------------------

    @Test
    void theDownloadIsThePdfAttachedWithASafeName() throws Exception {
        makeCaseReady();
        generate(true);
        MvcResult download = send(get(reportsUrl("/1/download")), analyst, null);
        assertThat(status(download)).isEqualTo(200);
        assertThat(download.getResponse().getContentType()).isEqualTo("application/pdf");
        String disposition = download.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).startsWith("attachment").contains(caseView(caseId, analyst).get("reportId").asText() + "_v1.pdf");
        assertThat(download.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(download.getResponse().getHeader("Cache-Control")).contains("no-store");
        byte[] bytes = download.getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        assertThat(auditActionsSinceStart()).contains("REPORT_GENERATED", "REPORT_DOWNLOADED");
    }

    @Test
    void aTamperedStoredFileIsNotSent() throws Exception {
        makeCaseReady();
        generate(true);
        storage.objects.put("reports/" + caseId + "/v1.pdf", ReportFixtures.blankPdf(5));
        MvcResult download = send(get(reportsUrl("/1/download")), analyst, null);
        assertThat(status(download)).isEqualTo(503);
        assertThat(body(download).get("message").asText()).contains("integrity");
    }

    @Test
    void aFinalReportNeedsItsOwnPermissionToDownload() throws Exception {
        makeCaseReady();
        generate(true);
        jdbc.update("UPDATE reports.report_versions SET kind = 'FINAL', finalized_at = now()");
        assertThat(status(send(get(reportsUrl("/1/download")), analystNoGen, null))).isEqualTo(403);
        String withDownload = tokenFor(analystId, "analyst@example.com", "CASE_READ_ASSIGNED", "REPORT_DOWNLOAD_FINAL");
        assertThat(status(send(get(reportsUrl("/1/download")), withDownload, null))).isEqualTo(200);
    }

    // ---- who may do what -----------------------------------------------------------------------------------------

    @Test
    void generatingNeedsThePermissionAndTheCase() throws Exception {
        makeCaseReady();
        assertThat(status(send(post(reportsUrl("")), null, obj("acknowledgeWarnings", true)))).isEqualTo(401);
        assertThat(status(send(post(reportsUrl("")), analystNoGen, obj("acknowledgeWarnings", true)))).isEqualTo(403);
        UUID otherId = newAdmin("other@example.com", "Otto Other");
        String other = tokenFor(otherId, "other@example.com", "CASE_READ_ASSIGNED", "REPORT_GENERATE");
        assertThat(status(send(post(reportsUrl("")), other, obj("acknowledgeWarnings", true)))).as("not assigned").isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports.report_jobs", Integer.class)).isZero();
    }

    @Test
    void readingVersionsAndJobsNeedsAccessToTheCase() throws Exception {
        makeCaseReady();
        generate(true);
        UUID otherId = newAdmin("other@example.com", "Otto Other");
        String other = tokenFor(otherId, "other@example.com", "CASE_READ_ASSIGNED");
        assertThat(status(send(get(reportsUrl("")), other, null))).isEqualTo(403);
        assertThat(status(send(get(reportsUrl("/1/download")), other, null))).isEqualTo(403);
        String auditor = tokenFor(otherId, "other@example.com", "CASE_READ_ALL");
        assertThat(status(send(get(reportsUrl("")), auditor, null))).isEqualTo(200);
        assertThat(status(send(get(reportsUrl("/1/download")), auditor, null))).as("drafts need only read access").isEqualTo(200);
    }

    // ---- the whole way, with the real browser ------------------------------------------------------------------------

    @Test
    void endToEndWithTheRealBrowserAndAnUploadedDocument() throws Exception {
        assumeTrue(BrowserLocator.find(null).isPresent(), "no Chrome / Chromium / Edge found: set CHROMIUM_PATH to run this test");
        makeCaseReady();
        byte[] picture = ReportFixtures.png(700, 500, new Color(200, 220, 240));
        MockMultipartFile file = new MockMultipartFile("file", "scan.png", "image/png", picture);
        var upload = multipart("/api/checks/" + checkId + "/documents").file(file).header("Authorization", "Bearer " + analyst);
        assertThat(mvc.perform(upload).andReturn().getResponse().getStatus()).isEqualTo(201);

        renderer.mode = SwitchablePdfRenderer.Mode.REAL;
        renderer.delayMillis = 0;
        JsonNode job = generate(true);
        assertThat(job.get("status").asText()).as(job.toString()).isEqualTo("DONE");

        MvcResult download = send(get(reportsUrl("/1/download")), analyst, null);
        try (PDDocument pdf = Loader.loadPDF(download.getResponse().getContentAsByteArray())) {
            assertThat(pdf.getNumberOfPages()).as("cover, detail page, services").isEqualTo(3);
            var text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).toLowerCase();
            assertThat(text).contains("asha rao", "emp-1001", "education verification", "document 1");
        }
        JsonNode version = body(send(get(reportsUrl("")), analyst, null)).get(0);
        assertThat(version.get("pageCount").asInt()).isEqualTo(3);
        assertThat(Arrays.asList(version.get("warnings").size())).containsExactly(0);
    }
}
