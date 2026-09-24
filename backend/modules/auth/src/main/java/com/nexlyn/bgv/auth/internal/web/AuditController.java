package com.nexlyn.bgv.auth.internal.web;

import com.nexlyn.bgv.auth.internal.service.AuditQueryService;
import com.nexlyn.bgv.common.web.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** {@code GET /api/audit-log}: needs {@code AUDIT_READ}. Read only; nothing can edit or delete the log. */
@RestController
public class AuditController {

    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    /**
     * @param actor  an admin id or email
     * @param entity an entity type such as ADMIN, ROLE, INVITATION
     * @param from   inclusive lower bound, ISO-8601 instant
     * @param to     exclusive upper bound, ISO-8601 instant
     */
    @GetMapping("/api/audit-log")
    public PageResponse<AuditQueryService.AuditEntry> search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return audit.search(new AuditQueryService.Filter(actor, action, entity, entityId, caseId, from, to), page, size);
    }
}
