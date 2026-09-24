package com.nexlyn.bgv.cases;

import java.util.UUID;

/** Published inside the transaction that deletes a check, so its documents can be retired with it. */
public record CheckDeletedEvent(UUID caseId, UUID checkId) {
}
