package com.nexlyn.bgv.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lets other modules show or check admins by id without touching the {@code auth} schema (they hold
 * only admin ids, CLAUDE.md {@literal §4.2} rule 4). Read only: names and emails, never secrets.
 */
public interface AdminDirectory {

    /** Who an admin is, as other modules may show them. */
    record AdminSummary(UUID id, String email, String fullName, boolean active) {
    }

    /** The admins with these ids (unknown ids are left out), keyed by id. */
    Map<UUID, AdminSummary> find(Collection<UUID> ids);

    /** Every active admin, by name. Used to pick someone to assign to a case. */
    List<AdminSummary> listActive();
}
