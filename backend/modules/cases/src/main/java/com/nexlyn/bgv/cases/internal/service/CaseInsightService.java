package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import com.nexlyn.bgv.cases.CaseDocumentLookup;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.Candidate;
import com.nexlyn.bgv.cases.internal.domain.ParentType;
import com.nexlyn.bgv.cases.internal.repository.CandidateRepository;
import com.nexlyn.bgv.cases.internal.repository.CaseRepository;
import com.nexlyn.bgv.cases.internal.service.CaseViews.ProgressView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.SectionProgress;
import com.nexlyn.bgv.cases.internal.service.CaseViews.ValidationIssue;
import com.nexlyn.bgv.cases.internal.service.CaseViews.ValidationResult;
import com.nexlyn.bgv.common.enums.CheckStatus;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What still needs doing on a case: the validation list (CLAUDE.md {@literal §7.1}) and the
 * per-section progress that drives the workspace navigator.
 *
 * <p>Errors block generating or submitting a report; warnings can be accepted with a confirmation.
 */
@Service
@PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
public class CaseInsightService {

    /** Section keys, in the order of the workspace navigator. */
    static final List<String[]> SECTIONS = List.of(
            new String[]{"report-info", "Report Info"},
            new String[]{"candidate", "Candidate Details"},
            new String[]{"verification-period", "Verification Period"},
            new String[]{"checks", "Checks"},
            new String[]{"overview", "Overview & Status"},
            new String[]{"remarks", "Remarks & Recommendation"},
            new String[]{"settings", "Report Settings"},
            new String[]{"generate", "Generate Report"});

    /** Sections whose completion counts towards the percentage (Generate is an action, not data). */
    private static final int DATA_SECTIONS = 7;

    private final CaseRepository cases;
    private final CandidateRepository candidates;
    private final ChecksSummary checks;
    private final CaseAccessPolicy policy;
    private final ObjectProvider<CaseDocumentLookup> documentLookup;

    public CaseInsightService(CaseRepository cases, CandidateRepository candidates, ChecksSummary checks,
                              CaseAccessPolicy policy, ObjectProvider<CaseDocumentLookup> documentLookup) {
        this.documentLookup = documentLookup;
        this.cases = cases;
        this.candidates = candidates;
        this.checks = checks;
        this.policy = policy;
    }

    /** The same answer as {@link #validate} without the caller check, for {@code CaseApi} (its callers check access). */
    @Transactional(readOnly = true)
    ValidationResult validationFor(UUID id) {
        BgvCase c = find(id);
        return validate(c, candidates.findByCaseId(id).orElseThrow(), checks.summariesOf(id), documentCounts(id));
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(UUID id) {
        policy.check(id, CaseAction.READ);
        BgvCase c = find(id);
        return validate(c, candidates.findByCaseId(id).orElseThrow(), checks.summariesOf(id), documentCounts(id));
    }

    @Transactional(readOnly = true)
    public ProgressView progress(UUID id) {
        policy.check(id, CaseAction.READ);
        BgvCase c = find(id);
        List<ChecksSummary.CheckSummary> summaries = checks.summariesOf(id);
        List<CheckStatus> statuses = summaries.stream().map(ChecksSummary.CheckSummary::status).toList();
        ValidationResult validation = validate(c, candidates.findByCaseId(id).orElseThrow(), summaries, documentCounts(id));

        Map<String, Integer> issues = new LinkedHashMap<>();
        for (ValidationIssue issue : concat(validation)) {
            issues.merge(issue.section(), 1, Integer::sum);
        }
        List<SectionProgress> sections = new ArrayList<>();
        int done = 0;
        for (String[] section : SECTIONS) {
            String key = section[0];
            int count = issues.getOrDefault(key, 0);
            boolean started = switch (key) {
                case "checks" -> !statuses.isEmpty();
                case "generate" -> false; // arrives with report generation (Phases 6-7)
                default -> c.getSavedSections().containsKey(key);
            };
            String state = !started ? "NOT_STARTED" : count > 0 ? "WARNING" : "SAVED";
            if (started && !key.equals("generate")) {
                done++;
            }
            sections.add(new SectionProgress(key, section[1], state, count));
        }
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (CheckStatus status : CheckStatus.values()) {
            byStatus.put(status.name(), 0);
        }
        for (CheckStatus status : statuses) {
            byStatus.merge(status.name(), 1, Integer::sum);
        }
        return new ProgressView(Math.round(done * 100f / DATA_SECTIONS), sections, statuses.size(), byStatus);
    }

    // ---- the rules of section 7.1 ---------------------------------------------------------------

    /** Documents per check, or null when the documents module is not there (the warning is then skipped). */
    private Map<UUID, Long> documentCounts(UUID caseId) {
        CaseDocumentLookup lookup = documentLookup.getIfAvailable();
        return lookup == null ? null : lookup.supportingDocumentCounts(caseId);
    }

    static ValidationResult validate(BgvCase c, Candidate candidate, List<ChecksSummary.CheckSummary> checkSummaries,
                                     Map<UUID, Long> documentCounts) {
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();

        // Errors: the report cannot be generated or submitted without these.
        if (isBlank(c.getReportId())) {
            errors.add(new ValidationIssue("report-info", "reportId", "Report ID is required."));
        }
        if (c.getIssueDate() == null) {
            errors.add(new ValidationIssue("report-info", "issueDate", "Issue date is required."));
        }
        if (isBlank(candidate.getFullName())) {
            errors.add(new ValidationIssue("candidate", "fullName", "Candidate's full name is required."));
        }
        if (isBlank(candidate.getEmployeeId())) {
            errors.add(new ValidationIssue("candidate", "employeeId", "Employee ID is required."));
        }
        if (checkSummaries.isEmpty()) {
            errors.add(new ValidationIssue("checks", "checks", "Add at least one verification check."));
        }

        // Warnings: allowed after a confirmation.
        if (isBlank(candidate.getParentName())) {
            String label = candidate.getParentType() == ParentType.GUARDIAN ? "Guardian's" : "Father's";
            warnings.add(new ValidationIssue("candidate", "parentName", label + " name is missing."));
        }
        if (candidate.getDob() == null) {
            warnings.add(new ValidationIssue("candidate", "dob", "Date of birth is missing."));
        }
        if (isBlank(candidate.getPhone())) {
            warnings.add(new ValidationIssue("candidate", "phone", "Phone number is missing."));
        }
        if (candidate.getPhotoDocumentId() == null) {
            warnings.add(new ValidationIssue("candidate", "photo", "Candidate photo is missing."));
        }
        if (c.isPeriodShow() && c.getPeriodStart() == null) {
            warnings.add(new ValidationIssue("verification-period", "start", "Verification period start date is missing."));
        }
        if (c.isPeriodShow() && c.getPeriodEnd() == null) {
            warnings.add(new ValidationIssue("verification-period", "end", "Verification period end date is missing."));
        }
        if (isBlank(c.getAnalystRemarks())) {
            warnings.add(new ValidationIssue("remarks", "analystRemarks", "Analyst remarks are empty."));
        }
        if (isBlank(c.getFinalRecommendation())) {
            warnings.add(new ValidationIssue("remarks", "finalRecommendation", "Final recommendation is empty."));
        }
        for (ChecksSummary.CheckSummary check : checkSummaries) {
            String name = check.title();
            if (check.requestedDate() == null) {
                warnings.add(new ValidationIssue("checks", "check:" + check.id() + ":requestedDate", name + ": requested date is missing."));
            }
            if (check.completedDate() == null) {
                warnings.add(new ValidationIssue("checks", "check:" + check.id() + ":completedDate", name + ": completed date is missing."));
            }
            if (!check.status().isConcluded()) {
                warnings.add(new ValidationIssue("checks", "check:" + check.id() + ":status", name + ": status is still " + check.status().label() + "."));
            }
            if (documentCounts != null && documentCounts.getOrDefault(check.id(), 0L) == 0L) {
                warnings.add(new ValidationIssue("checks", "check:" + check.id() + ":documents", name + ": no supporting document is attached."));
            }
            for (String label : check.missingRequired()) {
                warnings.add(new ValidationIssue("checks", "check:" + check.id() + ":" + label, name + ": " + label + " is missing."));
            }
        }
        return new ValidationResult(List.copyOf(errors), List.copyOf(warnings));
    }

    private static List<ValidationIssue> concat(ValidationResult result) {
        List<ValidationIssue> all = new ArrayList<>(result.errors());
        all.addAll(result.warnings());
        return all;
    }

    private BgvCase find(UUID id) {
        return cases.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Case not found."));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
