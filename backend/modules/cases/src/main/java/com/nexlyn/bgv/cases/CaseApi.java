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
}
