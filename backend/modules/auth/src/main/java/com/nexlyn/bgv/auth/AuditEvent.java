package com.nexlyn.bgv.auth;

import java.util.Map;
import java.util.UUID;

/**
 * Published by any module for anything that must land in the append-only audit log
 * (CLAUDE.md {@literal §11.4}). The {@code auth} module persists it. Never put passwords,
 * tokens or full identifiers in {@code before}/{@code after}.
 */
public record AuditEvent(
        String action,
        UUID actorId,
        String actorEmail,
        String entityType,
        String entityId,
        UUID caseId,
        String ip,
        String userAgent,
        Map<String, Object> before,
        Map<String, Object> after) {

    /** An event about an admin account, with no case and no before/after snapshot. */
    public static AuditEvent forAdmin(String action, UUID adminId, String adminEmail, String ip, String userAgent) {
        return new AuditEvent(action, adminId, adminEmail, "ADMIN",
                adminId == null ? null : adminId.toString(), null, ip, userAgent, null, null);
    }
}
