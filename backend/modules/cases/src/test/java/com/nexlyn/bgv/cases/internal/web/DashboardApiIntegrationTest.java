package com.nexlyn.bgv.cases.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexlyn.bgv.cases.CasesIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** The dashboard: counts by stage, my cases, cases waiting for me, due dates. */
class DashboardApiIntegrationTest extends CasesIntegrationTestBase {

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    UUID clientId;
    UUID annId;
    UUID benId;
    UUID reviewerId;
    String ann;        // preparer
    String ben;        // another preparer
    String reviewer;   // reads all, approves

    @BeforeEach
    void fixtures() throws Exception {
        clientId = UUID.fromString(body(send(post("/api/clients"), superToken(), obj("name", "Acme Corp", "displayName", "Acme Corp"))).get("id").asText());
        annId = newAdmin("ann@example.com", "Ann Analyst");
        benId = newAdmin("ben@example.com", "Ben Analyst");
        reviewerId = newAdmin("reviewer@example.com", "Rex Reviewer");
        String[] preparerPermissions = {"CASE_CREATE", "CASE_READ_ASSIGNED", "CASE_UPDATE", "CHECK_UPDATE", "REPORT_SUBMIT_FOR_REVIEW", "REPORT_APPROVE"};
        ann = tokenFor(annId, "ann@example.com", preparerPermissions);
        ben = tokenFor(benId, "ben@example.com", preparerPermissions);
        reviewer = tokenFor(reviewerId, "reviewer@example.com", "CASE_READ_ALL", "REPORT_APPROVE", "REPORT_FINALIZE");
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private String newCase(String token, LocalDate due) throws Exception {
        return body(send(post("/api/cases"), token, obj("clientId", clientId.toString(), "dueDate", due == null ? null : due.toString()))).get("id").asText();
    }

    private void submit(String caseId, String token) throws Exception {
        long version = body(send(get("/api/cases/" + caseId), token, null)).get("version").asLong();
        assertThat(status(send(put("/api/cases/" + caseId + "/candidate"), token,
                obj("version", version, "fullName", "Asha Rao", "parentType", "FATHER", "employeeId", "E-" + caseId.substring(0, 4))))).isEqualTo(200);
        assertThat(status(send(post("/api/cases/" + caseId + "/checks"), token, obj("type", "AADHAAR")))).isEqualTo(201);
        assertThat(status(send(post("/api/cases/" + caseId + "/submit-review"), token, obj("acknowledgeWarnings", true)))).isEqualTo(200);
    }

    private JsonNode dashboard(String token) throws Exception {
        return body(send(get("/api/dashboard"), token, null));
    }

    private static List<String> ids(JsonNode rows) {
        List<String> result = new ArrayList<>();
        rows.forEach(row -> result.add(row.get("id").asText()));
        return result;
    }

    // ---- counts --------------------------------------------------------------------------------------------------

    @Test
    void countsCasesByStageAndFollowsTheVisibilityRule() throws Exception {
        String a1 = newCase(ann, null);
        newCase(ann, null);
        String b1 = newCase(ben, null);
        submit(a1, ann);

        JsonNode forAnn = dashboard(ann).get("counts");
        assertThat(forAnn.get("DRAFT").asLong()).isEqualTo(1);
        assertThat(forAnn.get("IN_REVIEW").asLong()).isEqualTo(1);
        assertThat(forAnn.get("FINALIZED").asLong()).isZero();

        JsonNode forReviewer = dashboard(reviewer).get("counts");
        assertThat(forReviewer.get("DRAFT").asLong()).as("Ben's draft counts for someone who reads all").isEqualTo(2);
        assertThat(forReviewer.get("IN_REVIEW").asLong()).isEqualTo(1);

        JsonNode forBen = dashboard(ben).get("counts");
        assertThat(forBen.get("DRAFT").asLong()).as("Ben sees only his own").isEqualTo(1);
        assertThat(forBen.get("IN_REVIEW").asLong()).isZero();
        assertThat(b1).isNotEmpty();
    }

    // ---- my cases ----------------------------------------------------------------------------------------------------

    @Test
    void myCasesAreTheOnesIAmAssignedToThatAreNotFinalized() throws Exception {
        String mine = newCase(ann, null);
        String done = newCase(ann, null);
        newCase(ben, null);
        jdbc.update("UPDATE cases.cases SET lifecycle = 'FINALIZED' WHERE id = ?::uuid", done);

        JsonNode dash = dashboard(ann);
        assertThat(ids(dash.get("mine"))).containsExactly(mine);
        assertThat(dash.get("mine").get(0).get("reportId").asText()).startsWith("NX-");
        assertThat(dash.get("mine").get(0).get("clientName").asText()).isEqualTo("Acme Corp");

        // a reviewer who is assigned to nothing has no "mine", though they read every case
        assertThat(dashboard(reviewer).get("mine")).isEmpty();
        assertThat(dashboard(reviewer).get("counts").get("DRAFT").asLong()).isEqualTo(2);
    }

    // ---- awaiting my review --------------------------------------------------------------------------------------------

    @Test
    void casesWaitingForMyReviewLeaveOutTheOnesIMadeAndNeedThePermission() throws Exception {
        String annsCase = newCase(ann, null);
        String bensCase = newCase(ben, null);
        submit(annsCase, ann);
        submit(bensCase, ben);
        UUID id = UUID.fromString(bensCase);
        jdbc.update("INSERT INTO cases.case_assignments (case_id, admin_id, role_in_case, assigned_by) VALUES (?, ?, 'REVIEWER', ?)", UUID.fromString(annsCase), benId, benId);
        jdbc.update("INSERT INTO cases.case_assignments (case_id, admin_id, role_in_case, assigned_by) VALUES (?, ?, 'REVIEWER', ?)", id, annId, annId);

        assertThat(ids(dashboard(reviewer).get("awaitingMyReview"))).containsExactlyInAnyOrder(annsCase, bensCase);
        assertThat(ids(dashboard(ann).get("awaitingMyReview"))).as("Ann made hers, but may review Ben's").containsExactly(bensCase);
        assertThat(ids(dashboard(ben).get("awaitingMyReview"))).containsExactly(annsCase);

        String noApprove = tokenFor(reviewerId, "reviewer@example.com", "CASE_READ_ALL");
        assertThat(dashboard(noApprove).get("awaitingMyReview")).isEmpty();
    }

    @Test
    void approvedCasesNoLongerWaitForReview() throws Exception {
        String annsCase = newCase(ann, null);
        submit(annsCase, ann);
        assertThat(ids(dashboard(reviewer).get("awaitingMyReview"))).containsExactly(annsCase);
        assertThat(status(send(post("/api/cases/" + annsCase + "/approve"), reviewer, null))).isEqualTo(200);
        assertThat(dashboard(reviewer).get("awaitingMyReview")).isEmpty();
    }

    // ---- due dates -------------------------------------------------------------------------------------------------------

    @Test
    void dueSoonAndOverdue() throws Exception {
        String late = newCase(ann, TODAY.minusDays(2));
        String today = newCase(ann, TODAY);
        String soon = newCase(ann, TODAY.plusDays(3));
        newCase(ann, TODAY.plusDays(4));
        newCase(ann, null);
        String finished = newCase(ann, TODAY.minusDays(5));
        jdbc.update("UPDATE cases.cases SET lifecycle = 'FINALIZED' WHERE id = ?::uuid", finished);

        JsonNode dash = dashboard(ann);
        assertThat(ids(dash.get("dueSoon"))).as("earliest first; finalized and later ones left out").containsExactly(late, today, soon);
        assertThat(dash.get("overdue").asLong()).isEqualTo(1);
        assertThat(dash.get("today").asText()).isEqualTo(TODAY.toString());
        assertThat(dashboard(ben).get("dueSoon")).as("other people's cases are not visible").isEmpty();
        assertThat(dashboard(reviewer).get("overdue").asLong()).isEqualTo(1);
    }

    // ---- security -------------------------------------------------------------------------------------------------------------

    @Test
    void theDashboardNeedsSigningInAndSomeCaseReadPermission() throws Exception {
        assertThat(status(send(get("/api/dashboard"), null, null))).isEqualTo(401);
        String nothing = tokenFor(annId, "ann@example.com", "AUDIT_READ");
        assertThat(status(send(get("/api/dashboard"), nothing, null))).isEqualTo(403);
    }

    @Test
    void listsAreCappedAtEightCases() throws Exception {
        for (int i = 0; i < 10; i++) {
            newCase(ann, TODAY);
        }
        JsonNode dash = dashboard(ann);
        assertThat(dash.get("mine")).hasSize(8);
        assertThat(dash.get("dueSoon")).hasSize(8);
        assertThat(dash.get("counts").get("DRAFT").asLong()).isEqualTo(10);
    }
}
