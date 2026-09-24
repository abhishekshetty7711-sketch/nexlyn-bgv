package com.nexlyn.bgv.cases.internal.domain;

import com.nexlyn.bgv.common.enums.CaseLifecycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One line of a case's history: a change of status, who made it, and any comment. Never changed or removed. */
@Entity
@Table(schema = "cases", name = "case_status_history")
public class CaseStatusHistory {

    public enum Action { SUBMIT, APPROVE, REQUEST_CHANGES, FINALIZE, REOPEN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false)
    private CaseLifecycle fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private CaseLifecycle toStatus;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(updatable = false)
    private String comment;

    @Column(name = "report_version", updatable = false)
    private Integer reportVersion;

    @Column(nullable = false, updatable = false)
    private Instant at;

    protected CaseStatusHistory() {
    }

    public CaseStatusHistory(UUID caseId, Action action, CaseLifecycle from, CaseLifecycle to, UUID actorId, String comment,
                             Integer reportVersion, Instant at) {
        this.caseId = caseId;
        this.action = action;
        this.fromStatus = from;
        this.toStatus = to;
        this.actorId = actorId;
        this.comment = comment;
        this.reportVersion = reportVersion;
        this.at = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public Action getAction() {
        return action;
    }

    public CaseLifecycle getFromStatus() {
        return fromStatus;
    }

    public CaseLifecycle getToStatus() {
        return toStatus;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getComment() {
        return comment;
    }

    public Integer getReportVersion() {
        return reportVersion;
    }

    public Instant getAt() {
        return at;
    }
}
