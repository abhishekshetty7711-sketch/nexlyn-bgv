package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.CaseAssignmentLookup;
import com.nexlyn.bgv.cases.internal.repository.CaseAssignmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Answers the auth module's question "is this case assigned to this admin?" (CLAUDE.md
 * {@literal §11.4}, layer 3). Any role counts, preparer or reviewer.
 */
@Component
class CaseAssignmentLookupImpl implements CaseAssignmentLookup {

    private final CaseAssignmentRepository assignments;

    CaseAssignmentLookupImpl(CaseAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAssigned(UUID caseId, UUID adminId) {
        return assignments.existsByKeyCaseIdAndKeyAdminId(caseId, adminId);
    }
}
