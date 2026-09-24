package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.AdminDirectory;
import com.nexlyn.bgv.auth.AdminDirectory.AdminSummary;
import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.Candidate;
import com.nexlyn.bgv.cases.internal.domain.CaseAssignment;
import com.nexlyn.bgv.cases.internal.domain.Client;
import com.nexlyn.bgv.cases.internal.repository.CandidateRepository;
import com.nexlyn.bgv.cases.internal.repository.CaseAssignmentRepository;
import com.nexlyn.bgv.cases.internal.repository.ClientRepository;
import com.nexlyn.bgv.cases.internal.service.CaseViews.AssignmentView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.CandidateView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.CaseView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.ClientRef;
import com.nexlyn.bgv.cases.internal.service.CaseViews.OverviewView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.PeriodView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.RemarksView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.SettingsView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.WorkflowInfo;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.common.validation.IndianPhone;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds the frontend shapes from entities. Reads only; call inside a transaction. */
@Component
class CaseViewAssembler {

    private final ClientRepository clients;
    private final CandidateRepository candidates;
    private final CaseAssignmentRepository assignments;
    private final AdminDirectory directory;
    private final ChecksSummary checks;
    private final WorkflowRules rules;
    private final AuthApi auth;

    CaseViewAssembler(ClientRepository clients, CandidateRepository candidates, CaseAssignmentRepository assignments,
                      AdminDirectory directory, ChecksSummary checks, WorkflowRules rules, AuthApi auth) {
        this.rules = rules;
        this.auth = auth;
        this.clients = clients;
        this.candidates = candidates;
        this.assignments = assignments;
        this.directory = directory;
        this.checks = checks;
    }

    CaseView view(BgvCase c) {
        Client client = clients.findById(c.getClientId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Client not found."));
        Candidate candidate = candidates.findByCaseId(c.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Candidate not found."));
        OverviewCalculator.Overview auto = OverviewCalculator.auto(checks.statusesOf(c.getId()));
        return new CaseView(c.getId(), c.getReportId(), c.getLifecycle(), c.getLifecycle().isEditable(), c.getVersion(),
                c.getIssueDate(), c.getDueDate(), c.getReviewComment(),
                new ClientRef(client.getId(), client.getName(), client.getDisplayName()), c.getCompanyDisplayName(),
                candidateView(candidate),
                new PeriodView(c.isPeriodShow(), c.getPeriodStart(), c.getPeriodEnd()),
                new OverviewView(c.getStatusPreset(), c.getStatusTitle(), c.getStatusSubtitle(), c.getTotalOverride(),
                        c.getCompletedOverride(), c.getOverallStatusOverride(), auto,
                        OverviewCalculator.effective(auto, c.getTotalOverride(), c.getCompletedOverride(),
                                c.getOverallStatusOverride())),
                new RemarksView(c.getAnalystRemarks(), c.getFinalRecommendation()),
                new SettingsView(c.getLayoutCards(), c.getDateFormat(), c.isWatermarkEnabled(), c.getWatermarkText()),
                assignmentViews(assignments.findAllByKeyCaseId(c.getId())),
                c.getSavedSections(), c.getCreatedAt(), c.getUpdatedAt(), workflow(c));
    }

    /** Who took each review step (names through the auth directory) and what the current admin may do next. */
    private WorkflowInfo workflow(BgvCase c) {
        List<UUID> people = java.util.stream.Stream.of(c.getSubmittedBy(), c.getReviewedBy(), c.getFinalizedBy())
                .filter(java.util.Objects::nonNull).toList();
        Map<UUID, AdminSummary> names = people.isEmpty() ? Map.of() : directory.find(people);
        UUID me = auth.currentAdmin().map(AdminPrincipal::id).orElse(null);
        return new WorkflowInfo(c.getSubmittedAt(), nameOf(names, c.getSubmittedBy()), c.getReviewedAt(),
                nameOf(names, c.getReviewedBy()), c.getApprovedAt(), c.getFinalizedAt(), nameOf(names, c.getFinalizedBy()),
                rules.actionsFor(c, me));
    }

    private static String nameOf(Map<UUID, AdminSummary> names, UUID id) {
        if (id == null) {
            return null;
        }
        AdminSummary person = names.get(id);
        return person == null ? "Unknown admin" : person.fullName();
    }

    static CandidateView candidateView(Candidate candidate) {
        return new CandidateView(candidate.getFullName(), candidate.getParentType(), candidate.getParentName(),
                candidate.getEmployeeId(), candidate.getDob(), candidate.getPhone(),
                IndianPhone.format(candidate.getPhone()), candidate.getStreet(), candidate.getCity(),
                candidate.getState(), candidate.getPin(), candidate.getCountry(), candidate.getPhotoDocumentId() != null,
                candidate.getPhotoDocumentId());
    }

    /** Assignment lines with the admins' names filled in (looked up through the auth module's public directory). */
    List<AssignmentView> assignmentViews(Collection<CaseAssignment> rows) {
        Map<UUID, AdminSummary> people = directory.find(rows.stream().map(CaseAssignment::getAdminId).collect(Collectors.toSet()));
        return rows.stream()
                .map(row -> {
                    AdminSummary person = people.get(row.getAdminId());
                    return new AssignmentView(row.getAdminId(), person == null ? "Unknown admin" : person.fullName(),
                            person == null ? null : person.email(), row.getRole(), row.getAssignedAt());
                })
                .sorted(Comparator.comparing(AssignmentView::role).thenComparing(AssignmentView::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
