package com.nexlyn.bgv.cases.internal.domain;

import com.nexlyn.bgv.common.enums.CaseLifecycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One background-verification case: everything on the report except the checks (Phase 4) and the
 * documents (Phase 5). Named {@code BgvCase} because {@code Case} reads badly next to SQL and Java keywords.
 * Every change to any section goes through this class so the version (optimistic lock) always moves.
 */
@Entity
@Table(schema = "cases", name = "cases")
public class BgvCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private String reportId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    /** Overrides the client's display name on this report only. */
    @Column(name = "company_display_name")
    private String companyDisplayName;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "period_show", nullable = false)
    private boolean periodShow = true;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_preset", nullable = false)
    private StatusPreset statusPreset = StatusPreset.COMPLETED;

    @Column(name = "status_title", nullable = false)
    private String statusTitle = StatusPreset.COMPLETED.title();

    @Column(name = "status_subtitle", nullable = false)
    private String statusSubtitle = StatusPreset.COMPLETED.subtitle();

    @Column(name = "total_override")
    private Integer totalOverride;

    @Column(name = "completed_override")
    private Integer completedOverride;

    @Column(name = "overall_status_override")
    private String overallStatusOverride;

    @Column(name = "analyst_remarks")
    private String analystRemarks;

    @Column(name = "final_recommendation")
    private String finalRecommendation;

    @Column(name = "layout_cards", nullable = false)
    private short layoutCards = 4;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_format", nullable = false)
    private DateFormat dateFormat = DateFormat.NUMERIC;

    @Column(name = "watermark_enabled", nullable = false)
    private boolean watermarkEnabled;

    @Column(name = "watermark_text", nullable = false)
    private String watermarkText = "NEXLYN VERIFIED";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseLifecycle lifecycle = CaseLifecycle.DRAFT;

    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "finalized_by")
    private UUID finalizedBy;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    /** Section key to the moment it was last saved; drives the navigator's saved / not-started marks. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "saved_sections", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> savedSections = new HashMap<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private long version;

    protected BgvCase() {
    }

    public BgvCase(String reportId, UUID clientId, LocalDate issueDate, LocalDate dueDate, UUID createdBy) {
        this.reportId = reportId;
        this.clientId = clientId;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---- section changes --------------------------------------------------------------------

    /** Records that a section was saved, by whom and when, so the version moves even if no field changed. */
    public void markSaved(String section, Instant now, UUID by) {
        Map<String, String> copy = new HashMap<>(savedSections);
        copy.put(section, now.toString());
        savedSections = copy; // a new map, so the change is noticed and written
        updatedBy = by;
        updatedAt = now;
    }

    public void applyReportInfo(String reportId, LocalDate issueDate, UUID clientId, String companyDisplayName, LocalDate dueDate) {
        this.reportId = reportId;
        this.issueDate = issueDate;
        this.clientId = clientId;
        this.companyDisplayName = companyDisplayName;
        this.dueDate = dueDate;
    }

    public void applyPeriod(boolean show, LocalDate start, LocalDate end) {
        this.periodShow = show;
        this.periodStart = start;
        this.periodEnd = end;
    }

    public void applyOverview(StatusPreset preset, String title, String subtitle, Integer totalOverride,
                              Integer completedOverride, String overallStatusOverride) {
        this.statusPreset = preset;
        this.statusTitle = title;
        this.statusSubtitle = subtitle;
        this.totalOverride = totalOverride;
        this.completedOverride = completedOverride;
        this.overallStatusOverride = overallStatusOverride;
    }

    public void applyRemarks(String analystRemarks, String finalRecommendation) {
        this.analystRemarks = analystRemarks;
        this.finalRecommendation = finalRecommendation;
    }

    public void applySettings(short layoutCards, DateFormat dateFormat, boolean watermarkEnabled, String watermarkText) {
        this.layoutCards = layoutCards;
        this.dateFormat = dateFormat;
        this.watermarkEnabled = watermarkEnabled;
        this.watermarkText = watermarkText;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    // ---- the review workflow (CLAUDE.md section 11.3) ----------------------------------------------
    // Each step checks where the case is and refuses otherwise; who may take the step is the service's job.

    /** The preparer hands the case over: DRAFT or CHANGES_REQUESTED to IN_REVIEW. */
    public void submitForReview(UUID by, Instant now) {
        requireState("sent for review", CaseLifecycle.DRAFT, CaseLifecycle.CHANGES_REQUESTED);
        this.lifecycle = CaseLifecycle.IN_REVIEW;
        this.submittedBy = by;
        this.submittedAt = now;
        this.reviewComment = null;
        this.approvedAt = null;
        touchWorkflow(by, now);
    }

    /** IN_REVIEW to APPROVED. */
    public void approve(UUID by, String comment, Instant now) {
        requireState("approved", CaseLifecycle.IN_REVIEW);
        this.lifecycle = CaseLifecycle.APPROVED;
        this.reviewedBy = by;
        this.reviewedAt = now;
        this.approvedAt = now;
        this.reviewComment = comment;
        touchWorkflow(by, now);
    }

    /** IN_REVIEW back to the preparer. */
    public void requestChanges(UUID by, String comment, Instant now) {
        requireState("sent back for changes", CaseLifecycle.IN_REVIEW);
        this.lifecycle = CaseLifecycle.CHANGES_REQUESTED;
        this.reviewedBy = by;
        this.reviewedAt = now;
        this.approvedAt = null;
        this.reviewComment = comment;
        touchWorkflow(by, now);
    }

    /** APPROVED to FINALIZED: from here nothing changes any more. */
    public void finalizeCase(UUID by, Instant now) {
        requireState("finalized", CaseLifecycle.APPROVED);
        this.lifecycle = CaseLifecycle.FINALIZED;
        this.finalizedBy = by;
        this.finalizedAt = now;
        touchWorkflow(by, now);
    }

    /** FINALIZED to DRAFT, to make changes and go through review again. The old reports stay. */
    public void reopen(UUID by, String reason, Instant now) {
        requireState("reopened", CaseLifecycle.FINALIZED);
        this.lifecycle = CaseLifecycle.DRAFT;
        this.submittedBy = null;
        this.submittedAt = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.approvedAt = null;
        this.finalizedBy = null;
        this.finalizedAt = null;
        this.reviewComment = "Reopened: " + reason;
        touchWorkflow(by, now);
    }

    private void requireState(String what, CaseLifecycle... allowed) {
        for (CaseLifecycle state : allowed) {
            if (lifecycle == state) {
                return;
            }
        }
        throw new IllegalStateException("A case in state " + lifecycle + " cannot be " + what);
    }

    private void touchWorkflow(UUID by, Instant now) {
        this.updatedBy = by;
        this.updatedAt = now;
    }

    // ---- reads ------------------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getReportId() {
        return reportId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getCompanyDisplayName() {
        return companyDisplayName;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public boolean isPeriodShow() {
        return periodShow;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public StatusPreset getStatusPreset() {
        return statusPreset;
    }

    public String getStatusTitle() {
        return statusTitle;
    }

    public String getStatusSubtitle() {
        return statusSubtitle;
    }

    public Integer getTotalOverride() {
        return totalOverride;
    }

    public Integer getCompletedOverride() {
        return completedOverride;
    }

    public String getOverallStatusOverride() {
        return overallStatusOverride;
    }

    public String getAnalystRemarks() {
        return analystRemarks;
    }

    public String getFinalRecommendation() {
        return finalRecommendation;
    }

    public short getLayoutCards() {
        return layoutCards;
    }

    public DateFormat getDateFormat() {
        return dateFormat;
    }

    public boolean isWatermarkEnabled() {
        return watermarkEnabled;
    }

    public String getWatermarkText() {
        return watermarkText;
    }

    public CaseLifecycle getLifecycle() {
        return lifecycle;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public UUID getFinalizedBy() {
        return finalizedBy;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public Map<String, String> getSavedSections() {
        return Map.copyOf(savedSections);
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
