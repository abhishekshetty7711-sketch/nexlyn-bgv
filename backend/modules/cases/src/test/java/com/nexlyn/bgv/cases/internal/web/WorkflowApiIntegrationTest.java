package com.nexlyn.bgv.cases.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexlyn.bgv.cases.CasesIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** The review workflow and the maker-checker rule (CLAUDE.md section 11.3). */
class WorkflowApiIntegrationTest extends CasesIntegrationTestBase {

    UUID clientId;
    UUID preparerId;
    UUID reviewerId;
    String preparer;      // prepares the case and may send it for review; also (wrongly) holds REPORT_APPROVE, to prove the rule
    String reviewer;      // reads every case, approves, finalizes
    String caseId;

    @BeforeEach
    void fixtures() throws Exception {
        clientId = UUID.fromString(body(send(post("/api/clients"), superToken(), obj("name", "Acme Corp", "displayName", "Acme Corp"))).get("id").asText());
        preparerId = newAdmin("preparer@example.com", "Pia Preparer");
        reviewerId = newAdmin("reviewer@example.com", "Rex Reviewer");
        preparer = tokenFor(preparerId, "preparer@example.com", "CASE_CREATE", "CASE_READ_ASSIGNED", "CASE_UPDATE", "CHECK_UPDATE",
                "REPORT_SUBMIT_FOR_REVIEW", "REPORT_APPROVE", "REPORT_FINALIZE", "CASE_ASSIGN");
        reviewer = tokenFor(reviewerId, "reviewer@example.com", "CASE_READ_ALL", "REPORT_APPROVE", "REPORT_FINALIZE", "CASE_ASSIGN");
        caseId = body(send(post("/api/cases"), preparer, obj("clientId", clientId.toString()))).get("id").asText();
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private JsonNode view(String token) throws Exception {
        return body(send(get("/api/cases/" + caseId), token, null));
    }

    private void makeCaseReady() throws Exception {
        MvcResult saved = send(put("/api/cases/" + caseId + "/candidate"), preparer,
                obj("version", view(preparer).get("version").asLong(), "fullName", "Asha Rao", "parentType", "FATHER", "employeeId", "EMP-1"));
        assertThat(status(saved)).as(saved.getResponse().getContentAsString()).isEqualTo(200);
        MvcResult check = send(post("/api/cases/" + caseId + "/checks"), preparer, obj("type", "AADHAAR"));
        assertThat(status(check)).isEqualTo(201);
    }

    private MvcResult step(String action, String token, Object body) throws Exception {
        return send(post("/api/cases/" + caseId + "/" + action), token, body);
    }

    private void submit() throws Exception {
        makeCaseReady();
        MvcResult submitted = step("submit-review", preparer, obj("acknowledgeWarnings", true));
        assertThat(status(submitted)).as(submitted.getResponse().getContentAsString()).isEqualTo(200);
    }

    private String lifecycle() throws Exception {
        return view(superToken()).get("lifecycle").asText();
    }

    // ---- the happy path --------------------------------------------------------------------------------------------

    @Test
    void aCaseGoesFromDraftThroughReviewToApprovedWithAHistory() throws Exception {
        makeCaseReady();
        MvcResult submitted = step("submit-review", preparer, obj("acknowledgeWarnings", true));
        assertThat(status(submitted)).isEqualTo(200);
        JsonNode inReview = body(submitted);
        assertThat(inReview.get("lifecycle").asText()).isEqualTo("IN_REVIEW");
        assertThat(inReview.get("editable").asBoolean()).isFalse();
        assertThat(inReview.get("workflow").get("submittedByName").asText()).isEqualTo("Pia Preparer");

        MvcResult approved = step("approve", reviewer, obj("comment", "  Looks good.  "));
        assertThat(status(approved)).as(approved.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode done = body(approved);
        assertThat(done.get("lifecycle").asText()).isEqualTo("APPROVED");
        assertThat(done.get("reviewComment").asText()).isEqualTo("Looks good.");
        assertThat(done.get("workflow").get("reviewedByName").asText()).isEqualTo("Rex Reviewer");
        assertThat(done.get("workflow").get("approvedAt").isNull()).isFalse();

        JsonNode history = body(send(get("/api/cases/" + caseId + "/history"), reviewer, null));
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("action").asText()).isEqualTo("SUBMIT");
        assertThat(history.get(0).get("from").asText()).isEqualTo("DRAFT");
        assertThat(history.get(0).get("to").asText()).isEqualTo("IN_REVIEW");
        assertThat(history.get(0).get("actorName").asText()).isEqualTo("Pia Preparer");
        assertThat(history.get(1).get("action").asText()).isEqualTo("APPROVE");
        assertThat(history.get(1).get("comment").asText()).isEqualTo("Looks good.");
        assertThat(auditActionsSinceStart()).contains("CASE_SUBMITTED_FOR_REVIEW", "CASE_APPROVED");
    }

    @Test
    void changesRequestedSendsItBackAndTheCommentIsWhatThePreparerSees() throws Exception {
        submit();
        assertThat(status(step("request-changes", reviewer, obj("comment", "  ")))).as("a reason is required").isEqualTo(400);
        assertThat(status(step("request-changes", reviewer, null))).isEqualTo(400);
        assertThat(lifecycle()).isEqualTo("IN_REVIEW");

        MvcResult sentBack = step("request-changes", reviewer, obj("comment", "The employee ID looks wrong."));
        assertThat(status(sentBack)).isEqualTo(200);
        JsonNode back = body(sentBack);
        assertThat(back.get("lifecycle").asText()).isEqualTo("CHANGES_REQUESTED");
        assertThat(back.get("reviewComment").asText()).isEqualTo("The employee ID looks wrong.");
        assertThat(back.get("editable").asBoolean()).as("editable again").isTrue();

        // the preparer fixes it and sends it again; the old comment is cleared
        MvcResult fixed = send(put("/api/cases/" + caseId + "/candidate"), preparer,
                obj("version", back.get("version").asLong(), "fullName", "Asha Rao", "parentType", "FATHER", "employeeId", "EMP-2"));
        assertThat(status(fixed)).isEqualTo(200);
        MvcResult again = step("submit-review", preparer, obj("acknowledgeWarnings", true));
        assertThat(body(again).get("lifecycle").asText()).isEqualTo("IN_REVIEW");
        assertThat(body(again).get("reviewComment").isNull()).isTrue();
        assertThat(body(send(get("/api/cases/" + caseId + "/history"), reviewer, null)).size()).isEqualTo(3);
        assertThat(auditActionsSinceStart()).contains("CASE_CHANGES_REQUESTED");
    }

    // ---- maker and checker ----------------------------------------------------------------------------------------

    @Test
    void whoeverPreparedTheCaseCanNeverApproveOrSendItBack() throws Exception {
        submit();
        MvcResult approve = step("approve", preparer, null);
        assertThat(status(approve)).isEqualTo(403);
        assertThat(body(approve).get("message").asText()).contains("prepared or submitted").contains("approve");
        assertThat(status(step("request-changes", preparer, obj("comment", "self review")))).isEqualTo(403);
        assertThat(lifecycle()).as("nothing moved").isEqualTo("IN_REVIEW");
    }

    @Test
    void notEvenASuperAdminWhoCreatedTheCaseMayApproveIt() throws Exception {
        String ownersCase = body(send(post("/api/cases"), superToken(), obj("clientId", clientId.toString()))).get("id").asText();
        assertThat(status(send(put("/api/cases/" + ownersCase + "/candidate"), superToken(),
                obj("version", body(send(get("/api/cases/" + ownersCase), superToken(), null)).get("version").asLong(),
                        "fullName", "Asha Rao", "parentType", "FATHER", "employeeId", "EMP-9")))).isEqualTo(200);
        assertThat(status(send(post("/api/cases/" + ownersCase + "/checks"), superToken(), obj("type", "AADHAAR")))).isEqualTo(201);
        assertThat(status(send(post("/api/cases/" + ownersCase + "/submit-review"), superToken(), obj("acknowledgeWarnings", true)))).isEqualTo(200);

        MvcResult own = send(post("/api/cases/" + ownersCase + "/approve"), superToken(), null);
        assertThat(status(own)).isEqualTo(403);
        assertThat(status(send(post("/api/cases/" + ownersCase + "/approve"), reviewer, null))).as("someone else can").isEqualTo(200);
    }

    @Test
    void theSubmitterCannotApproveEvenWithoutBeingAssignedAsPreparer() throws Exception {
        makeCaseReady();
        // an OPS manager (reads all cases) sends the case for review on the preparer's behalf
        UUID opsId = newAdmin("ops@example.com", "Olga Ops");
        String ops = tokenFor(opsId, "ops@example.com", "CASE_READ_ALL", "REPORT_SUBMIT_FOR_REVIEW", "REPORT_APPROVE");
        assertThat(status(send(post("/api/cases/" + caseId + "/submit-review"), ops, obj("acknowledgeWarnings", true)))).isEqualTo(200);
        assertThat(status(send(post("/api/cases/" + caseId + "/approve"), ops, null))).isEqualTo(403);
        assertThat(status(step("approve", reviewer, null))).isEqualTo(200);
    }

    @Test
    void aPreparerAddedLaterCannotApproveEitherAndPreparersCannotBeSwappedDuringReview() throws Exception {
        submit();
        assertThat(status(send(post("/api/cases/" + caseId + "/assignments"), reviewer, obj("adminId", reviewerId.toString(), "role", "PREPARER"))))
                .as("no new preparer while in review").isEqualTo(409);
        assertThat(status(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/cases/" + caseId + "/assignments/" + preparerId), reviewer, null)))
                .as("the preparer cannot be removed while in review").isEqualTo(409);
        assertThat(status(send(post("/api/cases/" + caseId + "/assignments"), reviewer, obj("adminId", reviewerId.toString(), "role", "REVIEWER"))))
                .as("a reviewer can be assigned").isBetween(200, 201);
    }

    // ---- who may do what -----------------------------------------------------------------------------------------------

    @Test
    void eachStepNeedsItsOwnPermissionAndAccessToTheCase() throws Exception {
        makeCaseReady();
        assertThat(status(step("submit-review", null, null))).isEqualTo(401);
        String noSubmit = tokenFor(preparerId, "preparer@example.com", "CASE_READ_ASSIGNED");
        assertThat(status(step("submit-review", noSubmit, obj("acknowledgeWarnings", true)))).isEqualTo(403);

        UUID strangerId = newAdmin("stranger@example.com", "Sam Stranger");
        String stranger = tokenFor(strangerId, "stranger@example.com", "CASE_READ_ASSIGNED", "REPORT_SUBMIT_FOR_REVIEW", "REPORT_APPROVE");
        assertThat(status(step("submit-review", stranger, obj("acknowledgeWarnings", true)))).as("not assigned").isEqualTo(403);

        submit0();
        String noApprove = tokenFor(reviewerId, "reviewer@example.com", "CASE_READ_ALL");
        assertThat(status(step("approve", noApprove, null))).isEqualTo(403);
        assertThat(status(step("request-changes", noApprove, obj("comment", "x")))).isEqualTo(403);
        assertThat(status(step("approve", stranger, null))).as("approver must reach the case").isEqualTo(403);
        assertThat(status(send(get("/api/cases/" + caseId + "/history"), stranger, null))).isEqualTo(403);
    }

    private void submit0() throws Exception {
        assertThat(status(step("submit-review", preparer, obj("acknowledgeWarnings", true)))).isEqualTo(200);
    }

    // ---- the checklist ---------------------------------------------------------------------------------------------------

    @Test
    void errorsBlockSubmittingAndWarningsNeedAnAcknowledgement() throws Exception {
        MvcResult blocked = step("submit-review", preparer, obj("acknowledgeWarnings", true));
        assertThat(status(blocked)).isEqualTo(409);
        assertThat(body(blocked).get("message").asText()).contains("cannot be submitted yet").contains("full name");

        makeCaseReady();
        MvcResult unacknowledged = step("submit-review", preparer, obj("acknowledgeWarnings", false));
        assertThat(status(unacknowledged)).isEqualTo(409);
        assertThat(body(unacknowledged).get("message").asText()).contains("warning").contains("confirm");
        assertThat(status(step("submit-review", preparer, null))).as("no body = not acknowledged").isEqualTo(409);
        assertThat(lifecycle()).isEqualTo("DRAFT");
    }

    // ---- states ------------------------------------------------------------------------------------------------------------

    @Test
    void stepsOutOfOrderAreRefused() throws Exception {
        makeCaseReady();
        assertThat(status(step("approve", reviewer, null))).as("a draft cannot be approved").isEqualTo(409);
        assertThat(status(step("request-changes", reviewer, obj("comment", "x")))).isEqualTo(409);
        assertThat(status(step("reopen", reviewer, obj("reason", "x")))).as("only finalized cases reopen").isEqualTo(409);
        submit0();
        assertThat(status(step("submit-review", preparer, obj("acknowledgeWarnings", true)))).as("already in review").isEqualTo(409);
        assertThat(status(step("approve", reviewer, null))).isEqualTo(200);
        assertThat(status(step("approve", reviewer, null))).as("already approved").isEqualTo(409);
        assertThat(status(step("request-changes", reviewer, obj("comment", "too late")))).isEqualTo(409);
    }

    @Test
    void theCaseIsLockedInReviewAndWhenApproved() throws Exception {
        submit();
        long version = view(preparer).get("version").asLong();
        assertThat(status(send(put("/api/cases/" + caseId + "/candidate"), preparer,
                obj("version", version, "fullName", "Changed", "parentType", "FATHER", "employeeId", "E")))).isEqualTo(409);
        assertThat(status(send(post("/api/cases/" + caseId + "/checks"), preparer, obj("type", "PAN")))).isEqualTo(409);
        assertThat(status(step("approve", reviewer, null))).isEqualTo(200);
        assertThat(status(send(post("/api/cases/" + caseId + "/checks"), preparer, obj("type", "PAN")))).isEqualTo(409);
        assertThat(status(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/cases/" + caseId), superToken(), null)))
                .as("an approved (not finalized) case can still be deleted by someone allowed").isEqualTo(204);
    }

    // ---- what the screen may offer --------------------------------------------------------------------------------------------

    @Test
    void theCaseViewSaysWhatTheCurrentAdminMayDoNext() throws Exception {
        makeCaseReady();
        JsonNode draftForPreparer = view(preparer).get("workflow").get("actions");
        assertThat(draftForPreparer.get("canSubmit").asBoolean()).isTrue();
        assertThat(draftForPreparer.get("canApprove").asBoolean()).isFalse();
        assertThat(view(reviewer).get("workflow").get("actions").get("canSubmit").asBoolean()).as("no submit permission").isFalse();

        submit0();
        assertThat(view(preparer).get("workflow").get("actions").get("canApprove").asBoolean()).as("the preparer is the maker").isFalse();
        JsonNode inReviewForReviewer = view(reviewer).get("workflow").get("actions");
        assertThat(inReviewForReviewer.get("canApprove").asBoolean()).isTrue();
        assertThat(inReviewForReviewer.get("canRequestChanges").asBoolean()).isTrue();
        assertThat(inReviewForReviewer.get("canSubmit").asBoolean()).isFalse();
        assertThat(inReviewForReviewer.get("canFinalize").asBoolean()).isFalse();

        step("approve", reviewer, null);
        JsonNode approved = view(reviewer).get("workflow").get("actions");
        assertThat(approved.get("canFinalize").asBoolean()).isTrue();
        assertThat(approved.get("canApprove").asBoolean()).isFalse();
        assertThat(view(preparer).get("workflow").get("actions").get("canFinalize").asBoolean()).isFalse();
    }

    // ---- reopening ---------------------------------------------------------------------------------------------------------

    @Test
    void aFinalizedCaseIsReopenedToDraftWithAReasonAndKeepsItsHistory() throws Exception {
        submit();
        step("approve", reviewer, null);
        jdbc.update("UPDATE cases.cases SET lifecycle = 'FINALIZED', finalized_at = now(), finalized_by = ?::uuid WHERE id = ?::uuid", reviewerId.toString(), caseId);

        assertThat(view(reviewer).get("workflow").get("actions").get("canReopen").asBoolean()).isTrue();
        assertThat(view(preparer).get("workflow").get("actions").get("canReopen").asBoolean()).as("preparer holds the permission here").isTrue();
        assertThat(status(step("reopen", reviewer, obj("reason", " ")))).as("a reason is required").isEqualTo(400);
        String noPermission = tokenFor(reviewerId, "reviewer@example.com", "CASE_READ_ALL", "REPORT_APPROVE");
        assertThat(status(step("reopen", noPermission, obj("reason", "Client asked for a correction")))).isEqualTo(403);

        MvcResult reopened = step("reopen", reviewer, obj("reason", "Client asked for a correction"));
        assertThat(status(reopened)).isEqualTo(200);
        JsonNode draft = body(reopened);
        assertThat(draft.get("lifecycle").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("editable").asBoolean()).isTrue();
        assertThat(draft.get("reviewComment").asText()).isEqualTo("Reopened: Client asked for a correction");
        assertThat(draft.get("workflow").get("approvedAt").isNull()).isTrue();
        assertThat(draft.get("workflow").get("finalizedAt").isNull()).isTrue();

        List<String> actions = new ArrayList<>();
        body(send(get("/api/cases/" + caseId + "/history"), reviewer, null)).forEach(e -> actions.add(e.get("action").asText()));
        assertThat(actions).containsExactly("SUBMIT", "APPROVE", "REOPEN");
        assertThat(auditActionsSinceStart()).contains("CASE_REOPENED");
        // and a new round of review is needed: the preparer can submit again
        assertThat(status(step("submit-review", preparer, obj("acknowledgeWarnings", true)))).isEqualTo(200);
    }

    @Test
    void aFinalizedCaseCannotBeEditedOrDeleted() throws Exception {
        jdbc.update("UPDATE cases.cases SET lifecycle = 'FINALIZED' WHERE id = ?::uuid", caseId);
        long version = view(preparer).get("version").asLong();
        assertThat(status(send(put("/api/cases/" + caseId + "/candidate"), preparer,
                obj("version", version, "fullName", "Changed", "parentType", "FATHER", "employeeId", "E")))).isEqualTo(409);
        assertThat(status(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/cases/" + caseId), superToken(), null))).isEqualTo(409);
        assertThat(status(step("submit-review", preparer, obj("acknowledgeWarnings", true)))).isEqualTo(409);
    }

    // ---- history is append-only ------------------------------------------------------------------------------------------------

    @Test
    void theHistoryCannotBeChangedOrRemoved() throws Exception {
        submit();
        assertThatThrownBy(() -> jdbc.update("UPDATE cases.case_status_history SET comment = 'edited'")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM cases.case_status_history")).hasMessageContaining("append-only");
    }
}
