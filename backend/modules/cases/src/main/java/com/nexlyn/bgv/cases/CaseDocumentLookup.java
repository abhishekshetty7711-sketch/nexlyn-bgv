package com.nexlyn.bgv.cases;

import java.util.Map;
import java.util.UUID;

/**
 * Implemented by the {@code documents} module, which owns the files. Lets the cases module ask about
 * documents (for validation and image blocks) without reading another module's schema. When nothing
 * implements it, the cases module simply skips those checks.
 */
public interface CaseDocumentLookup {

    /** How many supporting documents each check has (checks with none are absent from the map). */
    Map<UUID, Long> supportingDocumentCounts(UUID caseId);

    /** True when the document is an uploaded image for this check's free image block. */
    boolean isFreeImageOf(UUID documentId, UUID caseId, UUID checkId);
}
