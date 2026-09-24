package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.cases.internal.domain.CaseRole;
import com.nexlyn.bgv.cases.internal.domain.DateFormat;
import com.nexlyn.bgv.cases.internal.domain.ParentType;
import com.nexlyn.bgv.cases.internal.domain.StatusPreset;
import com.nexlyn.bgv.common.enums.CaseLifecycle;
import com.nexlyn.bgv.cases.internal.service.OverviewCalculator.Overview;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The shapes returned to the frontend. Records only: no entity ever leaves the service layer. */
public final class CaseViews {

    private CaseViews() {
    }

    public record ClientRef(UUID id, String name, String displayName) {
    }

    public record CandidateView(String fullName, ParentType parentType, String parentName, String employeeId,
                                LocalDate dob, String phone, String phoneDisplay, String street, String city,
                                String state, String pin, String country, boolean hasPhoto,
                                UUID photoDocumentId) {
    }

    public record PeriodView(boolean show, LocalDate start, LocalDate end) {
    }

    public record OverviewView(StatusPreset statusPreset, String statusTitle, String statusSubtitle,
                               Integer totalOverride, Integer completedOverride, String overallStatusOverride,
                               Overview auto, Overview effective) {
    }

    public record RemarksView(String analystRemarks, String finalRecommendation) {
    }

    public record SettingsView(int layoutCards, DateFormat dateFormat, boolean watermarkEnabled, String watermarkText) {
    }

    public record AssignmentView(UUID adminId, String fullName, String email, CaseRole role, Instant assignedAt) {
    }

    /** What the current admin may do with this case right now (the same rules the server enforces on each call). */
    public record WorkflowActions(boolean canSubmit, boolean canApprove, boolean canRequestChanges, boolean canFinalize,
                                  boolean canReopen) {
        public static WorkflowActions none() {
            return new WorkflowActions(false, false, false, false, false);
        }
    }

    /** Who took the review steps and when, and what the current admin may do next. */
    public record WorkflowInfo(Instant submittedAt, String submittedByName, Instant reviewedAt, String reviewedByName,
                               Instant approvedAt, Instant finalizedAt, String finalizedByName, WorkflowActions actions) {
    }

    /** One line of a case's history. */
    public record HistoryEntry(String action, CaseLifecycle from, CaseLifecycle to, UUID actorId, String actorName,
                               String comment, Integer reportVersion, Instant at) {
    }

    /** The whole workspace of one case. {@code version} must be sent back with every save. */
    public record CaseView(UUID id, String reportId, CaseLifecycle lifecycle, boolean editable, long version,
                           LocalDate issueDate, LocalDate dueDate, String reviewComment, ClientRef client,
                           String companyDisplayName, CandidateView candidate, PeriodView period,
                           OverviewView overview, RemarksView remarks, SettingsView settings,
                           List<AssignmentView> assignments, Map<String, String> savedSections,
                           Instant createdAt, Instant updatedAt, WorkflowInfo workflow) {
    }

    /** One line of the case list. */
    public record CaseRow(UUID id, String reportId, String clientName, String candidateName, String employeeId,
                          CaseLifecycle lifecycle, LocalDate issueDate, LocalDate dueDate,
                          List<AssignmentView> assignments, int savedSections, Instant updatedAt) {
    }

    public record ValidationIssue(String section, String field, String message) {
    }

    public record ValidationResult(List<ValidationIssue> errors, List<ValidationIssue> warnings) {
    }

    public record SectionProgress(String key, String label, String state, int issues) {
    }

    public record ProgressView(int percent, List<SectionProgress> sections, int totalChecks,
                               Map<String, Integer> checksByStatus) {
    }
}
