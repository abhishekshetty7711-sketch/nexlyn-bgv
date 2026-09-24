package com.nexlyn.bgv.cases.internal.web;

import com.nexlyn.bgv.cases.internal.service.CaseViews.CaseView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.HistoryEntry;
import com.nexlyn.bgv.cases.internal.service.WorkflowService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The review workflow (CLAUDE.md section 9.2). Thin: every rule is in the service. Each step answers with the whole case. */
@RestController
@RequestMapping("/api/cases/{id}")
public class WorkflowController {

    record SubmitRequest(boolean acknowledgeWarnings) {
    }

    record CommentRequest(String comment) {
    }

    record ReopenRequest(String reason) {
    }

    private final WorkflowService workflow;

    public WorkflowController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/submit-review")
    public ResponseEntity<CaseView> submit(@PathVariable UUID id, @RequestBody(required = false) SubmitRequest body) {
        return noStore(workflow.submitForReview(id, body != null && body.acknowledgeWarnings()));
    }

    @PostMapping("/approve")
    public ResponseEntity<CaseView> approve(@PathVariable UUID id, @RequestBody(required = false) CommentRequest body) {
        return noStore(workflow.approve(id, body == null ? null : body.comment()));
    }

    @PostMapping("/request-changes")
    public ResponseEntity<CaseView> requestChanges(@PathVariable UUID id, @RequestBody(required = false) CommentRequest body) {
        return noStore(workflow.requestChanges(id, body == null ? null : body.comment()));
    }

    @PostMapping("/reopen")
    public ResponseEntity<CaseView> reopen(@PathVariable UUID id, @RequestBody(required = false) ReopenRequest body) {
        return noStore(workflow.reopen(id, body == null ? null : body.reason()));
    }

    @GetMapping("/history")
    public List<HistoryEntry> history(@PathVariable UUID id) {
        return workflow.history(id);
    }

    private static ResponseEntity<CaseView> noStore(CaseView view) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(view);
    }
}
