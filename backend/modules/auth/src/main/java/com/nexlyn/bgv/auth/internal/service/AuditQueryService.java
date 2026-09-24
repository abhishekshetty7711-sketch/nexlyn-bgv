package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.internal.domain.AuditLog;
import com.nexlyn.bgv.auth.internal.repository.AuditLogRepository;
import com.nexlyn.bgv.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Read access to the audit log (CLAUDE.md {@literal §9.1}: {@code GET /api/audit-log}). Read only, newest first. */
@Service
@PreAuthorize("hasAuthority('AUDIT_READ')")
public class AuditQueryService {

    public record Filter(String actor, String action, String entity, String entityId, UUID caseId,
                         Instant from, Instant to) {
    }

    public record AuditEntry(UUID id, Instant at, UUID actorId, String actorEmail, String action, String entityType,
                             String entityId, UUID caseId, String ip, Map<String, Object> before,
                             Map<String, Object> after, String correlationId) {
    }

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository auditLog;

    public AuditQueryService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> search(Filter filter, int page, int size) {
        Page<AuditLog> result = auditLog.findAll(specification(filter), PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "at")));
        return new PageResponse<>(result.map(AuditQueryService::entry).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements());
    }

    private static Specification<AuditLog> specification(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (f.actor() != null && !f.actor().isBlank()) {
                UUID asId = parseUuid(f.actor());
                all.add(asId != null
                        ? cb.equal(root.get("actorId"), asId)
                        : cb.equal(cb.lower(root.get("actorEmail")), f.actor().trim().toLowerCase(Locale.ROOT)));
            }
            if (f.action() != null && !f.action().isBlank()) {
                all.add(cb.equal(root.get("action"), f.action().trim()));
            }
            if (f.entity() != null && !f.entity().isBlank()) {
                all.add(cb.equal(root.get("entityType"), f.entity().trim().toUpperCase(Locale.ROOT)));
            }
            if (f.entityId() != null && !f.entityId().isBlank()) {
                all.add(cb.equal(root.get("entityId"), f.entityId().trim()));
            }
            if (f.caseId() != null) {
                all.add(cb.equal(root.get("caseId"), f.caseId()));
            }
            if (f.from() != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("at"), f.from()));
            }
            if (f.to() != null) {
                all.add(cb.lessThan(root.get("at"), f.to()));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private static AuditEntry entry(AuditLog row) {
        return new AuditEntry(row.getId(), row.getAt(), row.getActorId(), row.getActorEmail(), row.getAction(),
                row.getEntityType(), row.getEntityId(), row.getCaseId(), row.getIp(), row.getBefore(), row.getAfter(),
                row.getCorrelationId());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
