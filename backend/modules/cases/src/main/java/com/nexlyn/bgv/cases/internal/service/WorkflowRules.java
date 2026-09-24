package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.CaseRole;
import com.nexlyn.bgv.cases.internal.repository.CaseAssignmentRepository;
import com.nexlyn.bgv.cases.internal.service.CaseViews.WorkflowActions;
import com.nexlyn.bgv.common.enums.CaseLifecycle;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The maker-checker rule (CLAUDE.md section 11.3): whoever prepared a case can never approve or finalize
 * it, whatever permissions or roles they hold (a super admin included). "Prepared" means: assigned as its
 * preparer, or created it, or sent it for review. One place decides, so what the screen offers and what the
 * server allows can never disagree.
 */
@Component
class WorkflowRules {

    private final CaseAssignmentRepository assignments;
    private final CaseAccessPolicy policy;

    WorkflowRules(CaseAssignmentRepository assignments, CaseAccessPolicy policy) {
        this.assignments = assignments;
        this.policy = policy;
    }

    /** True when this admin is one of the case's makers and so may not approve or finalize it. */
    boolean isMaker(BgvCase c, UUID adminId) {
        if (adminId == null) {
            return false;
        }
        return adminId.equals(c.getCreatedBy())
                || adminId.equals(c.getSubmittedBy())
                || assignments.findAllByKeyCaseIdAndKeyAdminId(c.getId(), adminId).stream().anyMatch(a -> a.getRole() == CaseRole.PREPARER);
    }

    /** What the signed-in admin may do now; all false when nobody is signed in. */
    WorkflowActions actionsFor(BgvCase c, UUID adminId) {
        if (adminId == null) {
            return WorkflowActions.none();
        }
        CaseLifecycle state = c.getLifecycle();
        boolean maker = isMaker(c, adminId);
        return new WorkflowActions(
                state.isEditable() && policy.isAllowed(c.getId(), CaseAction.SUBMIT_FOR_REVIEW),
                state == CaseLifecycle.IN_REVIEW && !maker && policy.isAllowed(c.getId(), CaseAction.APPROVE),
                state == CaseLifecycle.IN_REVIEW && !maker && policy.isAllowed(c.getId(), CaseAction.APPROVE),
                state == CaseLifecycle.APPROVED && !maker && policy.isAllowed(c.getId(), CaseAction.FINALIZE),
                state == CaseLifecycle.FINALIZED && policy.isAllowed(c.getId(), CaseAction.REOPEN));
    }
}
