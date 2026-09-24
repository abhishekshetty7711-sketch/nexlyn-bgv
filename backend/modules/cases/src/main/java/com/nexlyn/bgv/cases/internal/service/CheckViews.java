package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.cases.internal.checktype.FieldType;
import com.nexlyn.bgv.cases.internal.domain.FieldSource;
import com.nexlyn.bgv.cases.internal.domain.FreeSectionKind;
import com.nexlyn.bgv.common.enums.CheckStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The shapes returned for checks. Sensitive values only ever appear masked here. */
public final class CheckViews {

    private CheckViews() {
    }

    /**
     * How a check's requested / completed dates are set: the first check is the MASTER, later checks
     * follow it automatically (AUTO) until someone types their own dates (MANUAL).
     */
    public enum DateSync { MASTER, AUTO, MANUAL }

    /** {@code value} is the masked form for a sensitive field; {@code hasValue} says whether one is stored. */
    public record FieldView(String key, String label, FieldType type, boolean sensitive, boolean required, String value,
                            boolean hasValue, boolean verifiedTick, boolean manual, FieldSource source) {
    }

    public record DetailView(String label, String value) {
    }

    public record FreeSectionView(UUID id, FreeSectionKind kind, String text, UUID documentId, int sortOrder) {
    }

    public record CheckView(UUID id, UUID caseId, String type, String displayName, String documentName, String groupKey,
                            String title, String summaryDescription, String thisCardVerifies, CheckStatus status,
                            String verificationType, LocalDate requestedDate, LocalDate completedDate, DateSync dateSync,
                            String remarks, boolean hasAttestation, String barCouncilNo, String disclaimer, int sortOrder,
                            long version, List<FieldView> fields, List<DetailView> details,
                            List<FreeSectionView> freeSections, Instant updatedAt) {
    }

    /** The one place a real sensitive value is returned, after a permission check and an audit entry. */
    public record RevealedValue(String fieldKey, String value) {
    }
}
