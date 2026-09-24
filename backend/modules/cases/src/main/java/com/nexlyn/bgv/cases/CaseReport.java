package com.nexlyn.bgv.cases;

import com.nexlyn.bgv.common.enums.CaseLifecycle;
import com.nexlyn.bgv.common.enums.CheckStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the report needs to know about one case, as plain data (CLAUDE.md section 13, step 1).
 * Values are as stored: dates are ISO dates (formatting is the renderer's job), sensitive numbers are
 * <b>masked</b> (the report never receives a full Aadhaar / PAN / UAN), and labels already follow the
 * candidate's "Father / Guardian" choice.
 */
public record CaseReport(
        UUID caseId,
        String reportId,
        CaseLifecycle lifecycle,
        LocalDate issueDate,
        /** The client's name as it prints; may contain line breaks. */
        String companyName,
        Candidate candidate,
        Period period,
        Pill pill,
        Overview overview,
        /** Bold-only HTML (already sanitised). */
        String analystRemarks,
        /** Bold-only HTML (already sanitised). */
        String finalRecommendation,
        Settings settings,
        List<Check> checks) {

    public record Candidate(String fullName, boolean guardian, String parentName, String employeeId, LocalDate dob,
                            String phoneDisplay, UUID photoDocumentId) {
    }

    public record Period(boolean show, LocalDate start, LocalDate end) {
    }

    /** The status pill on page 1. */
    public record Pill(String preset, String title, String subtitle) {
    }

    /** The overview numbers, with any manual overrides already applied. */
    public record Overview(int total, int completed, String overallStatus) {
    }

    /** {@code dateFormat} is NUMERIC (11/06/2026) or TEXT (11-Jun-2026); {@code layoutCards} is 4 or 6. */
    public record Settings(int layoutCards, String dateFormat, boolean watermarkEnabled, String watermarkText) {
    }

    /**
     * One verification. {@code iconGroup} decides how checks share a summary card (identity, court,
     * address, employment, education, or the check type itself). {@code cardVerifies} is what the detail
     * page shows next to the title; it already falls back to the summary description or document name.
     */
    public record Check(UUID id, String type, String iconGroup, String title, String summaryDescription,
                        String cardVerifies, String documentName, CheckStatus status, String verificationType,
                        LocalDate requestedDate, LocalDate completedDate, String remarks, boolean hasAttestation,
                        String barCouncilNo, String disclaimer, List<Field> fields, List<Detail> details,
                        List<FreeBlock> freeBlocks) {
    }

    /** {@code type} is the field kind (text, date, aadhaar, repeatable ...); {@code value} is masked for sensitive fields. */
    public record Field(String label, String type, String value, boolean verifiedTick) {
    }

    public record Detail(String label, String value) {
    }

    /** {@code kind} is TEXT or IMAGE; an image block points at a document. */
    public record FreeBlock(String kind, String text, UUID documentId) {
    }
}
