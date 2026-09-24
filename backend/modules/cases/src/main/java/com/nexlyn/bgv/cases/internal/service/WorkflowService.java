package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.AdminDirectory;
import com.nexlyn.bgv.auth.AdminDirectory.AdminSummary;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.CaseStatusHistory;
import com.nexlyn.bgv.cases.internal.domain.CaseStatusHistory.Action;
import com.nexlyn.bgv.cases.internal.repository.CaseRepository;
import com.nexlyn.bgv.cases.internal.repository.CaseStatusHistoryRepository;
import com.nexlyn.bgv.cases.internal.service.CaseViews.CaseView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.HistoryEntry;
import com.nexlyn.bgv.cases.internal.service.CaseViews.ValidationResult;
import com.nexlyn.bgv.common.enums.CaseLifecycle;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The review workflow (CLAUDE.md sections 9.2 and 11.3): submit, approve, request changes, reopen.
 * (Finalizing also needs the report PDF, so the reports module drives it through {@code CaseApi}.)
 *
 * <pre>DRAFT -> IN_REVIEW -> APPROVED -> FINALIZED      IN_REVIEW -> CHANGES_REQUESTED -> IN_REVIEW      FINALIZED -> DRAFT (reopen)</pre>
 *
 * <p>Each step needs its permission, access to the case, and the right starting state. Approving and
 * asking for changes are refused for anyone who prepared the case ({@link WorkflowRules}). Case data can
 * only change in DRAFT and CHANGES_REQUESTED; every other module already refuses edits in the other states.
 * Every step is written to the append-only history and to the audit log.
 */
@Service
public class WorkflowService {

    private static final int MAX_COMMENT = 2000;

    private final CaseRepository cases;
    private final CaseStatusHistoryRepository history;
    private final CaseInsightService insight;
    private final WorkflowRules rules;
    private final CaseViewAssembler assembler;
    private final CaseAccessPolicy policy;
    private final AuthApi auth;
    private final AdminDirectory directory;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WorkflowService(CaseRepository cases, CaseStatusHistoryRepository history, CaseInsightService insight,
                           WorkflowRules rules, CaseViewAssembler assembler, CaseAccessPolicy policy, AuthApi auth,
                           AdminDirectory directory, ApplicationEventPublisher events, Clock clock) {
        this.cases = cases;
        this.history = history;
        this.insight = insight;
        this.rules = rules;
        this.assembler = assembler;
        this.policy = policy;
        this.auth = auth;
        this.directory = directory;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Hands the case to a reviewer. Errors of the checklist (7.1) block it; warnings need
     * {@code acknowledgeWarnings}. From here the case is locked until it is approved or sent back.
     */
    @PreAuthorize("hasAuthority('REPORT_SUBMIT_FOR_REVIEW')")
    @Transactional
    public CaseView submitForReview(UUID caseId, boolean acknowledgeWarnings) {
        policy.check(caseId, CaseAction.SUBMIT_FOR_REVIEW);
        BgvCase c = find(caseId);
        requireState(c, "Only a draft or a case sent back for changes can be submitted.", CaseLifecycle.DRAFT, CaseLifecycle.CHANGES_REQUESTED);
        ValidationResult validation = insight.validationFor(caseId);
        if (!validation.errors().isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "The case cannot be submitted yet: "
                    + validation.errors().stream().map(CaseViews.ValidationIssue::message).collect(Collectors.joining(" ")));
        }
        if (!validation.warnings().isEmpty() && !acknowledgeWarnings) {
            throw new ApiException(ErrorCode.CONFLICT, "This case has " + validation.warnings().size()
                    + " warning(s). Review them and confirm to submit it anyway.");
        }
        UUID me = auth.requireCurrentAdmin().id();
        CaseLifecycle before = c.getLifecycle();
        c.submitForReview(me, Instant.now(clock));
        return record(c, Action.SUBMIT, before, me, null, null, "CASE_SUBMITTED_FOR_REVIEW");
    }

    @PreAuthorize("hasAuthority('REPORT_APPROVE')")
    @Transactional
    public CaseView approve(UUID caseId, String comment) {
        policy.check(caseId, CaseAction.APPROVE);
        BgvCase c = find(caseId);
        requireState(c, "Only a case that is in review can be approved.", CaseLifecycle.IN_REVIEW);
        UUID me = auth.requireCurrentAdmin().id();
        requireChecker(c, me, "approve");
        String text = optionalComment(comment);
        CaseLifecycle before = c.getLifecycle();
        c.approve(me, text, Instant.now(clock));
        return record(c, Action.APPROVE, before, me, text, null, "CASE_APPROVED");
    }

    /** Sends the case back to its preparer; the reason is required and is what the preparer sees. */
    @PreAuthorize("hasAuthority('REPORT_APPROVE')")
    @Transactional
    public CaseView requestChanges(UUID caseId, String comment) {
        policy.check(caseId, CaseAction.APPROVE);
        BgvCase c = find(caseId);
        requireState(c, "Only a case that is in review can be sent back.", CaseLifecycle.IN_REVIEW);
        UUID me = auth.requireCurrentAdmin().id();
        requireChecker(c, me, "send back");
        String text = comment == null ? "" : comment.trim();
        if (text.isEmpty() || text.length() > MAX_COMMENT) {
            throw invalid("comment", "must say what needs to change (at most " + MAX_COMMENT + " characters)");
        }
        CaseLifecycle before = c.getLifecycle();
        c.requestChanges(me, text, Instant.now(clock));
        return record(c, Action.REQUEST_CHANGES, before, me, text, null, "CASE_CHANGES_REQUESTED");
    }

    /**
     * Opens a finalized case for changes again. The finalized reports stay as they are; the next report
     * made is a new version, and the case must be reviewed and approved again.
     */
    @PreAuthorize("hasAuthority('REPORT_FINALIZE')")
    @Transactional
    public CaseView reopen(UUID caseId, String reason) {
        policy.check(caseId, CaseAction.REOPEN);
        BgvCase c = find(caseId);
        requireState(c, "Only a finalized case can be reopened.", CaseLifecycle.FINALIZED);
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty() || text.length() > MAX_COMMENT) {
            throw invalid("reason", "must say why the case is reopened (at most " + MAX_COMMENT + " characters)");
        }
        UUID me = auth.requireCurrentAdmin().id();
        CaseLifecycle before = c.getLifecycle();
        c.reopen(me, text, Instant.now(clock));
        return record(c, Action.REOPEN, before, me, text, null, "CASE_REOPENED");
    }

    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID caseId) {
        policy.check(caseId, CaseAction.READ);
        find(caseId);
        List<CaseStatusHistory> rows = history.findAllByCaseIdOrderByAtAscIdAsc(caseId);
        Map<UUID, AdminSummary> people = directory.find(rows.stream().map(CaseStatusHistory::getActorId).collect(Collectors.toSet()));
        return rows.stream().map(r -> {
            AdminSummary who = people.get(r.getActorId());
            return new HistoryEntry(r.getAction().name(), r.getFromStatus(), r.getToStatus(), r.getActorId(),
                    who == null ? "Unknown admin" : who.fullName(), r.getComment(), r.getReportVersion(), r.getAt());
        }).toList();
    }

    // ---- shared with CaseApi (finalize) -------------------------------------------------------------------

    /** Writes the history line and the audit event for a step that has just changed the case, and returns its new view. */
    CaseView record(BgvCase c, Action action, CaseLifecycle before, UUID actor, String comment, Integer reportVersion, String auditAction) {
        BgvCase saved = cases.saveAndFlush(c);
        history.save(new CaseStatusHistory(saved.getId(), action, before, saved.getLifecycle(), actor, comment, reportVersion, Instant.now(clock)));
        events.publishEvent(new AuditEvent(auditAction, null, null, "CASE", saved.getId().toString(), saved.getId(), null, null,
                Map.of("status", before.name()), Map.of("status", saved.getLifecycle().name(), "reportId", saved.getReportId())));
        return assembler.view(saved);
    }

    void requireChecker(BgvCase c, UUID adminId, String verb) {
        if (rules.isMaker(c, adminId)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "You prepared or submitted this case, so you cannot " + verb + " it. Another admin has to.");
        }
    }

    private BgvCase find(UUID caseId) {
        return cases.findByIdAndDeletedAtIsNull(caseId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Case not found."));
    }

    private static void requireState(BgvCase c, String message, CaseLifecycle... allowed) {
        for (CaseLifecycle state : allowed) {
            if (c.getLifecycle() == state) {
                return;
            }
        }
        throw new ApiException(ErrorCode.CONFLICT, message + " This case is " + c.getLifecycle().name().toLowerCase().replace('_', ' ') + ".");
    }

    private static String optionalComment(String comment) {
        String text = comment == null ? "" : comment.trim();
        if (text.length() > MAX_COMMENT) {
            throw invalid("comment", "must be at most " + MAX_COMMENT + " characters");
        }
        return text.isEmpty() ? null : text;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "The request is not valid.", List.of(new ApiError.FieldError(field, message)), null);
    }
}
