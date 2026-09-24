package com.nexlyn.bgv.cases.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A company that orders background verifications. */
@Entity
@Table(schema = "cases", name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** The name as printed on reports; may span several lines. */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "logo_document_id")
    private UUID logoDocumentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_check_types", nullable = false, columnDefinition = "jsonb")
    private List<String> defaultCheckTypes = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

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

    protected Client() {
    }

    public Client(String name, String displayName, List<String> defaultCheckTypes, boolean active, UUID createdBy) {
        this.name = name;
        this.displayName = displayName;
        this.defaultCheckTypes = new ArrayList<>(defaultCheckTypes);
        this.active = active;
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

    public void update(String name, String displayName, List<String> defaultCheckTypes, boolean active, UUID updatedBy) {
        this.name = name;
        this.displayName = displayName;
        this.defaultCheckTypes = new ArrayList<>(defaultCheckTypes);
        this.active = active;
        this.updatedBy = updatedBy;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDefaultCheckTypes() {
        return defaultCheckTypes;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
