package com.nexlyn.bgv.reports.internal.service;

import com.nexlyn.bgv.reports.internal.domain.ReportJob;
import com.nexlyn.bgv.reports.internal.domain.ReportVersion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The shapes returned to the frontend. */
public final class ReportViews {

    private ReportViews() {
    }

    public record JobView(UUID id, UUID caseId, ReportJob.Status status, Integer version, String error,
                          List<String> warnings, Instant requestedAt, Instant finishedAt) {
    }

    public record VersionView(int version, ReportVersion.Kind kind, long sizeBytes, int pageCount, boolean encrypted,
                              UUID generatedBy, String generatedByName, Instant generatedAt, Instant finalizedAt,
                              List<String> warnings) {
    }

    /** A stored PDF ready to send. */
    public record Download(byte[] bytes, String filename, boolean finalReport) {
    }
}
