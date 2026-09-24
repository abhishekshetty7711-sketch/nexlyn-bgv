package com.nexlyn.bgv.cases.internal.repository;

import com.nexlyn.bgv.cases.internal.domain.CaseAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CaseAssignmentRepository extends JpaRepository<CaseAssignment, CaseAssignment.Key> {

    List<CaseAssignment> findAllByKeyCaseId(UUID caseId);

    List<CaseAssignment> findAllByKeyCaseIdIn(Collection<UUID> caseIds);

    List<CaseAssignment> findAllByKeyCaseIdAndKeyAdminId(UUID caseId, UUID adminId);

    boolean existsByKeyCaseIdAndKeyAdminId(UUID caseId, UUID adminId);
}
