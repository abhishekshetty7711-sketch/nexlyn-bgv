package com.nexlyn.bgv.auth.internal.security;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import com.nexlyn.bgv.auth.CaseAssignmentLookup;
import com.nexlyn.bgv.common.security.Permission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Two questions, both must be "yes": does the admin hold the permission for this action, and (unless
 * they hold {@code CASE_READ_ALL}) is the case assigned to them? A case that does not exist is simply
 * "not assigned", so probing ids reveals nothing.
 */
@Service
class CaseAccessPolicyImpl implements CaseAccessPolicy {

    private final AuthApi auth;
    private final ObjectProvider<CaseAssignmentLookup> assignments;

    CaseAccessPolicyImpl(AuthApi auth, ObjectProvider<CaseAssignmentLookup> assignments) {
        this.auth = auth;
        this.assignments = assignments;
    }

    @Override
    public void check(UUID caseId, CaseAction action) {
        if (!isAllowed(caseId, action)) {
            throw new AccessDeniedException("Not allowed");
        }
    }

    @Override
    public boolean isAllowed(UUID caseId, CaseAction action) {
        AdminPrincipal admin = auth.currentAdmin().orElse(null);
        if (admin == null || caseId == null || !hasPermissionFor(admin, action)) {
            return false;
        }
        if (admin.hasPermission(Permission.CASE_READ_ALL.name())) {
            return true;
        }
        // Without CASE_READ_ALL only assigned cases are reachable. Until the cases module provides
        // assignments (Phase 3), nothing is assigned.
        CaseAssignmentLookup lookup = assignments.getIfAvailable();
        return lookup != null && lookup.isAssigned(caseId, admin.id());
    }

    private static boolean hasPermissionFor(AdminPrincipal admin, CaseAction action) {
        return requiredPermissions(action).stream().anyMatch(p -> admin.hasPermission(p.name()));
    }

    /** Any one of these permissions is enough. */
    static List<Permission> requiredPermissions(CaseAction action) {
        return switch (action) {
            case READ -> List.of(Permission.CASE_READ_ALL, Permission.CASE_READ_ASSIGNED);
            case UPDATE -> List.of(Permission.CASE_UPDATE);
            case DELETE -> List.of(Permission.CASE_DELETE);
            case ASSIGN -> List.of(Permission.CASE_ASSIGN);
            case UPDATE_CHECK -> List.of(Permission.CHECK_UPDATE);
            case UPLOAD_DOCUMENT -> List.of(Permission.DOCUMENT_UPLOAD);
            case DELETE_DOCUMENT -> List.of(Permission.DOCUMENT_DELETE);
            case GENERATE_REPORT -> List.of(Permission.REPORT_GENERATE);
            case SUBMIT_FOR_REVIEW -> List.of(Permission.REPORT_SUBMIT_FOR_REVIEW);
            case APPROVE -> List.of(Permission.REPORT_APPROVE);
            case FINALIZE -> List.of(Permission.REPORT_FINALIZE);
        };
    }
}
