package com.nexlyn.bgv.cases.internal.domain;

import com.nexlyn.bgv.common.enums.CheckStatus;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One verification (one card of the report): its status, dates, remarks and attestation. Values live in {@link CheckField}. */
@Entity
@Table(schema = "cases", name = "verification_checks")
public class VerificationCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    /** A check type code such as AADHAAR (see the YAML definitions). */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(name = "summary_description")
    private String summaryDescription;

    @Column(name = "this_card_verifies")
    private String thisCardVerifies;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckStatus status = CheckStatus.PENDING;

    @Column(name = "verification_type", nullable = false)
    private String verificationType = "Standard";

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    /** True once this check's dates were set by hand and no longer follow the first check. */
    @Column(name = "dates_manual", nullable = false)
    private boolean datesManual;

    private String remarks;

    @Column(name = "has_attestation", nullable = false)
    private boolean hasAttestation;

    /** The comments and the attestation print on a page of their own, right after this check's main page. */
    @Column(name = "comments_on_next_page", nullable = false)
    private boolean commentsOnNextPage;

    @Column(name = "bar_council_no")
    private String barCouncilNo;

    private String disclaimer;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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

    protected VerificationCheck() {
    }

    public VerificationCheck(UUID caseId, String type, String title, String verificationType, int sortOrder, UUID createdBy) {
        this.caseId = caseId;
        this.type = type;
        this.title = title;
        this.verificationType = verificationType;
        this.sortOrder = sortOrder;
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

    public void applyCard(String title, String summaryDescription, String thisCardVerifies, CheckStatus status,
                          String verificationType, String remarks, UUID by) {
        this.title = title;
        this.summaryDescription = summaryDescription;
        this.thisCardVerifies = thisCardVerifies;
        this.status = status;
        this.verificationType = verificationType;
        this.remarks = remarks;
        this.updatedBy = by;
    }

    public void applyDates(LocalDate requested, LocalDate completed, boolean manual) {
        this.requestedDate = requested;
        this.completedDate = completed;
        this.datesManual = manual;
    }

    public void applyCommentsPlacement(boolean onNextPage) {
        this.commentsOnNextPage = onNextPage;
    }

    public boolean isCommentsOnNextPage() {
        return commentsOnNextPage;
    }

    public void applyAttestation(boolean on, String barCouncilNo, String disclaimer) {
        this.hasAttestation = on;
        this.barCouncilNo = barCouncilNo;
        this.disclaimer = disclaimer;
    }

    /** Marks the check as changed even if only its fields changed, so the version moves. */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getSummaryDescription() {
        return summaryDescription;
    }

    public String getThisCardVerifies() {
        return thisCardVerifies;
    }

    public CheckStatus getStatus() {
        return status;
    }

    public String getVerificationType() {
        return verificationType;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public LocalDate getCompletedDate() {
        return completedDate;
    }

    public boolean isDatesManual() {
        return datesManual;
    }

    public String getRemarks() {
        return remarks;
    }

    public boolean isHasAttestation() {
        return hasAttestation;
    }

    public String getBarCouncilNo() {
        return barCouncilNo;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
