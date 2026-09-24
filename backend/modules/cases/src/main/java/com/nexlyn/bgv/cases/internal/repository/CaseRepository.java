package com.nexlyn.bgv.cases.internal.repository;

import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<BgvCase, UUID>, JpaSpecificationExecutor<BgvCase> {

    /** Deleted cases behave as if they do not exist. */
    Optional<BgvCase> findByIdAndDeletedAtIsNull(UUID id);

    /** Report IDs are unique across all cases, deleted ones included, so an ID is never reused. */
    Optional<BgvCase> findByReportIdIgnoreCase(String reportId);

    boolean existsByReportIdIgnoreCase(String reportId);

    long countByClientId(UUID clientId);
}
