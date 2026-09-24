package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.internal.domain.AuditLog;
import com.nexlyn.bgv.auth.internal.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;

/**
 * Persists every published {@link AuditEvent} to the append-only {@code auth.audit_log}. It runs in
 * the publisher's transaction, so an action and its audit row succeed or fail together. Missing
 * actor and request details are filled in from the current request.
 */
@Component
class AuditEventListener {

    private final AuditLogRepository auditLog;
    private final AuthApi auth;
    private final Clock clock;

    AuditEventListener(AuditLogRepository auditLog, AuthApi auth, Clock clock) {
        this.auditLog = auditLog;
        this.auth = auth;
        this.clock = clock;
    }

    @EventListener
    void on(AuditEvent event) {
        var actorId = event.actorId();
        var actorEmail = event.actorEmail();
        if (actorId == null && actorEmail == null) {
            AdminPrincipal actor = auth.currentAdmin().orElse(null);
            if (actor != null) {
                actorId = actor.id();
                actorEmail = actor.email();
            }
        }
        String ip = event.ip();
        String userAgent = event.userAgent();
        if (ip == null && userAgent == null && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            ip = request.getRemoteAddr();
            userAgent = request.getHeader("User-Agent");
        }
        auditLog.save(new AuditLog(Instant.now(clock), actorId, cut(actorEmail, 254), cut(event.action(), 100),
                cut(event.entityType(), 100), cut(event.entityId(), 100), event.caseId(), cut(ip, 45),
                cut(userAgent, 500), event.before(), event.after(), MDC.get("correlationId")));
    }

    /** Keeps an over-long value from breaking the business action the audit row belongs to. */
    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
