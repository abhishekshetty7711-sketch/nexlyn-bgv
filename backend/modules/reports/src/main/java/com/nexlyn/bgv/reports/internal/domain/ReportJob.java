package com.nexlyn.bgv.reports.internal.domain;

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

/** One request to make a report PDF, from waiting in the queue to done or failed. */
@Entity
@Table(schema = "reports", name = "report_jobs")
public class ReportJob {

    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.QUEUED;

    private Integer version;

    private String error;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> warnings;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ReportJob() {
    }

    public ReportJob(UUID caseId, UUID requestedBy, Instant now) {
        this.caseId = caseId;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
    }

    public void start(Instant now) {
        this.status = Status.RUNNING;
        this.startedAt = now;
    }

    public void succeed(int version, List<String> warnings, Instant now) {
        this.status = Status.DONE;
        this.version = version;
        this.warnings = warnings;
        this.finishedAt = now;
    }

    public void fail(String reason, Instant now) {
        this.status = Status.FAILED;
        this.error = reason;
        this.finishedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public Status getStatus() {
        return status;
    }

    public Integer getVersion() {
        return version;
    }

    public String getError() {
        return error;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
