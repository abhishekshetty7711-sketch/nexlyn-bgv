package com.nexlyn.bgv.reports.internal.domain;

import com.nexlyn.bgv.cases.CaseReport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One generated PDF of a case. Versions are never changed or removed: a new report is a new version. */
@Entity
@Table(schema = "reports", name = "report_versions")
public class ReportVersion {

    public enum Kind { DRAFT, FINAL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "pdf_storage_key", nullable = false, updatable = false)
    private String pdfStorageKey;

    @Column(nullable = false, updatable = false)
    private String sha256;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "page_count", nullable = false, updatable = false)
    private int pageCount;

    @Column(nullable = false)
    private boolean encrypted;

    @Column(name = "generated_by", nullable = false, updatable = false)
    private UUID generatedBy;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "finalized_by")
    private UUID finalizedBy;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> warnings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private CaseReport snapshot;

    protected ReportVersion() {
    }

    public ReportVersion(UUID caseId, int version, Kind kind, String pdfStorageKey, String sha256, long sizeBytes,
                         int pageCount, boolean encrypted, UUID generatedBy, Instant generatedAt, List<String> warnings,
                         CaseReport snapshot) {
        this.caseId = caseId;
        this.version = version;
        this.kind = kind;
        this.pdfStorageKey = pdfStorageKey;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.pageCount = pageCount;
        this.encrypted = encrypted;
        this.generatedBy = generatedBy;
        this.generatedAt = generatedAt;
        this.warnings = warnings;
        this.snapshot = snapshot;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public int getVersion() {
        return version;
    }

    public Kind getKind() {
        return kind;
    }

    public String getPdfStorageKey() {
        return pdfStorageKey;
    }

    public String getSha256() {
        return sha256;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getPageCount() {
        return pageCount;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public UUID getGeneratedBy() {
        return generatedBy;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public CaseReport getSnapshot() {
        return snapshot;
    }
}
