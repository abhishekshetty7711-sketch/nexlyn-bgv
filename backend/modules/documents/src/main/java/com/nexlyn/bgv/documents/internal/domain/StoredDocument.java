package com.nexlyn.bgv.documents.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One uploaded file. The bytes are in object storage under {@link #getStorageKey()}. */
@Entity
@Table(schema = "documents", name = "documents")
public class StoredDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "check_id")
    private UUID checkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentKind kind;

    private String label;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    private Integer width;
    private Integer height;

    @Enumerated(EnumType.STRING)
    private ImageQuality quality;

    @Column(name = "move_to_next_page", nullable = false)
    private boolean moveToNextPage;

    @Column(name = "use_larger_box", nullable = false)
    private boolean useLargerBox;

    @JdbcTypeCode(SqlTypes.JSON)
    private Crop crop;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    protected StoredDocument() {
    }

    public StoredDocument(UUID caseId, UUID checkId, DocumentKind kind, String storageKey, String originalFilename,
                          String mimeType, long sizeBytes, String sha256, Integer width, Integer height,
                          ImageQuality quality, int sortOrder, UUID uploadedBy, Instant now) {
        this.caseId = caseId;
        this.checkId = checkId;
        this.kind = kind;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.width = width;
        this.height = height;
        this.quality = quality;
        this.sortOrder = sortOrder;
        this.uploadedBy = uploadedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** How the picture is shown on the report. */
    public void applyPresentation(String label, boolean moveToNextPage, boolean useLargerBox, Crop crop, Instant now) {
        this.label = label;
        this.moveToNextPage = moveToNextPage;
        this.useLargerBox = useLargerBox;
        this.crop = crop;
        this.updatedAt = now;
    }

    public void moveTo(int sortOrder, Instant now) {
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public void retire(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isImage() {
        return mimeType.startsWith("image/");
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public UUID getCheckId() {
        return checkId;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public ImageQuality getQuality() {
        return quality;
    }

    public boolean isMoveToNextPage() {
        return moveToNextPage;
    }

    public boolean isUseLargerBox() {
        return useLargerBox;
    }

    public Crop getCrop() {
        return crop;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersion() {
        return version;
    }
}
