package com.nexlyn.bgv.cases.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** An admin working on a case as preparer or reviewer. Non-{@code CASE_READ_ALL} admins can only reach cases they are on. */
@Entity
@Table(schema = "cases", name = "case_assignments")
public class CaseAssignment {

    @Embeddable
    public record Key(
            @Column(name = "case_id") UUID caseId,
            @Column(name = "admin_id") UUID adminId,
            @Enumerated(EnumType.STRING) @Column(name = "role_in_case") CaseRole role) implements Serializable {
    }

    @EmbeddedId
    private Key key;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    protected CaseAssignment() {
    }

    public CaseAssignment(UUID caseId, UUID adminId, CaseRole role, Instant assignedAt, UUID assignedBy) {
        this.key = new Key(caseId, adminId, role);
        this.assignedAt = assignedAt;
        this.assignedBy = assignedBy;
    }

    public Key getKey() {
        return key;
    }

    public UUID getCaseId() {
        return key.caseId();
    }

    public UUID getAdminId() {
        return key.adminId();
    }

    public CaseRole getRole() {
        return key.role();
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
