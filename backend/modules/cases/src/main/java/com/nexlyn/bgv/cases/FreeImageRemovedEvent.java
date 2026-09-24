package com.nexlyn.bgv.cases;

import java.util.UUID;

/** Published inside the transaction that removes an image block from a check, so its file can be retired. */
public record FreeImageRemovedEvent(UUID caseId, UUID checkId, UUID documentId) {
}
