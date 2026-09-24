package com.nexlyn.bgv.cases.internal.repository;

import com.nexlyn.bgv.cases.internal.domain.VerificationCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationCheckRepository extends JpaRepository<VerificationCheck, UUID> {

    List<VerificationCheck> findAllByCaseIdOrderBySortOrderAsc(UUID caseId);

    Optional<VerificationCheck> findByIdAndCaseId(UUID id, UUID caseId);

    long countByCaseId(UUID caseId);
}
