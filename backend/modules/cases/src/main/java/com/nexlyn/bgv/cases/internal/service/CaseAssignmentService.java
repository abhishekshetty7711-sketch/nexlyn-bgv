package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.AdminDirectory;
import com.nexlyn.bgv.auth.AdminDirectory.AdminSummary;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.CaseAssignment;
import com.nexlyn.bgv.cases.internal.domain.CaseRole;
import com.nexlyn.bgv.cases.internal.repository.CaseAssignmentRepository;
import com.nexlyn.bgv.cases.internal.repository.CaseRepository;
import com.nexlyn.bgv.cases.internal.service.CaseViews.CaseView;
import com.nexlyn.bgv.common.enums.CaseLifecycle;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who works on a case (CLAUDE.md {@literal §9.2}). Assignment is what lets a preparer or reviewer see
 * a case at all, so it needs {@code CASE_ASSIGN}. One admin cannot be both preparer and reviewer of the
 * same case: that would defeat the maker-checker rule the workflow depends on (CLAUDE.md {@literal §11.3}).
 */
@Service
@PreAuthorize("hasAuthority('CASE_ASSIGN')")
public class CaseAssignmentService {

    private final CaseRepository cases;
    private final CaseAssignmentRepository assignments;
    private final AdminDirectory directory;
    private final CaseViewAssembler assembler;
    private final CaseAccessPolicy policy;
    private final AuthApi auth;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CaseAssignmentService(CaseRepository cases, CaseAssignmentRepository assignments, AdminDirectory directory,
                                 CaseViewAssembler assembler, CaseAccessPolicy policy, AuthApi auth,
                                 ApplicationEventPublisher events, Clock clock) {
        this.cases = cases;
        this.assignments = assignments;
        this.directory = directory;
        this.assembler = assembler;
        this.policy = policy;
        this.auth = auth;
        this.events = events;
        this.clock = clock;
    }

    /** Active admins who can be given a case. */
    @Transactional(readOnly = true)
    public List<AdminSummary> assignableAdmins() {
        return directory.listActive();
    }

    @Transactional
    public CaseView assign(UUID caseId, UUID adminId, CaseRole role) {
        policy.check(caseId, CaseAction.ASSIGN);
        BgvCase c = findOpen(caseId);
        AdminSummary person = directory.find(List.of(adminId)).get(adminId);
        if (person == null || !person.active()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose an active admin.",
                    List.of(new ApiError.FieldError("adminId", "is not an active admin")), null);
        }
        requireEditablePreparers(c, role);
        List<CaseAssignment> existing = assignments.findAllByKeyCaseIdAndKeyAdminId(caseId, adminId);
        if (existing.stream().anyMatch(a -> a.getRole() != role)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This admin already has the other role on this case. Nobody can be both preparer and reviewer.");
        }
        if (existing.isEmpty()) {
            assignments.saveAndFlush(new CaseAssignment(caseId, adminId, role, Instant.now(clock), auth.requireCurrentAdmin().id()));
            events.publishEvent(new AuditEvent("CASE_ASSIGNED", null, null, "CASE", caseId.toString(), caseId, null, null,
                    null, Map.of("adminId", adminId.toString(), "role", role.name())));
        }
        return assembler.view(c);
    }

    /** Removes one role, or every role when {@code role} is null. Removing someone who is not assigned is fine. */
    @Transactional
    public CaseView unassign(UUID caseId, UUID adminId, CaseRole role) {
        policy.check(caseId, CaseAction.ASSIGN);
        BgvCase c = findOpen(caseId);
        List<CaseAssignment> toRemove = assignments.findAllByKeyCaseIdAndKeyAdminId(caseId, adminId).stream()
                .filter(a -> role == null || a.getRole() == role)
                .toList();
        toRemove.forEach(a -> requireEditablePreparers(c, a.getRole()));
        if (!toRemove.isEmpty()) {
            assignments.deleteAll(toRemove);
            assignments.flush();
            events.publishEvent(new AuditEvent("CASE_UNASSIGNED", null, null, "CASE", caseId.toString(), caseId, null, null,
                    Map.of("adminId", adminId.toString(), "roles", toRemove.stream().map(a -> a.getRole().name()).toList()), null));
        }
        return assembler.view(c);
    }

    /** The preparers of a case in review or approved are what the maker-checker rule is measured against, so they cannot change then. */
    private static void requireEditablePreparers(BgvCase c, CaseRole role) {
        if (role == CaseRole.PREPARER && (c.getLifecycle() == CaseLifecycle.IN_REVIEW || c.getLifecycle() == CaseLifecycle.APPROVED)) {
            throw new ApiException(ErrorCode.CONFLICT, "The preparer cannot be changed while the case is in review or approved.");
        }
    }

    private BgvCase findOpen(UUID caseId) {
        BgvCase c = cases.findByIdAndDeletedAtIsNull(caseId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Case not found."));
        if (c.getLifecycle() == CaseLifecycle.FINALIZED) {
            throw new ApiException(ErrorCode.CONFLICT, "A finalized case cannot be reassigned.");
        }
        return c;
    }
}
