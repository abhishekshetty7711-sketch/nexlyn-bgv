package com.nexlyn.bgv.cases;

import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask the cases module (CLAUDE.md {@literal §4.2} rule 2). It answers questions
 * and makes the few changes other modules need; it does NOT check who is asking: the calling module
 * must call {@code CaseAccessPolicy} first.
 */
public interface CaseApi {

    /** The case a check belongs to, or empty if there is no such check (or its case was deleted). */
    Optional<UUID> caseIdOfCheck(UUID checkId);

    /** True when the case exists and is not deleted. */
    boolean caseExists(UUID caseId);

    /** Where the case is in its life; empty when it does not exist. */
    Optional<com.nexlyn.bgv.common.enums.CaseLifecycle> lifecycleOf(UUID caseId);

    /** True when the case exists, is not deleted, and is in draft or changes-requested (case data may change). */
    boolean isEditable(UUID caseId);

    /** True when the check exists and belongs to the case. */
    boolean checkBelongsToCase(UUID caseId, UUID checkId);

    /**
     * Makes this document the candidate's photo. Returns the previous photo's document id, if there was one,
     * so the caller can retire it. Does not change the case's version (an open form stays valid).
     */
    Optional<UUID> replaceCandidatePhoto(UUID caseId, UUID documentId);

    /** Removes the candidate's photo. Returns the removed document id, if there was one. */
    Optional<UUID> clearCandidatePhoto(UUID caseId);

    /** Everything the report needs, read in one consistent pass. Throws when the case does not exist. */
    CaseReport reportOf(UUID caseId);

    /** The errors and warnings of the case (CLAUDE.md section 7.1). Throws when the case does not exist. */
    CaseValidation validationOf(UUID caseId);

    /**
     * Checks that this admin may finalize the case now: it is APPROVED and the admin did not prepare it (the
     * maker-checker rule). Returns the moment it was approved, so the caller can make sure the report it
     * finalizes was made after that. Throws a plain-language error otherwise.
     */
    java.time.Instant requireCanFinalize(UUID caseId, UUID adminId);

    /** APPROVED to FINALIZED: writes the history line and the audit event. Call inside the caller's transaction. */
    void markFinalized(UUID caseId, UUID adminId, int reportVersion);
}
