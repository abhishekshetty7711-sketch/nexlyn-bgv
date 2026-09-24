package com.nexlyn.bgv.auth;

import java.util.UUID;

/**
 * Implemented by the {@code cases} module (Phase 3), which owns the assignment data. Lets the auth
 * module decide "is this case assigned to this admin" without reading another module's schema.
 * Until it is implemented, nobody is considered assigned to anything.
 */
public interface CaseAssignmentLookup {

    boolean isAssigned(UUID caseId, UUID adminId);
}
