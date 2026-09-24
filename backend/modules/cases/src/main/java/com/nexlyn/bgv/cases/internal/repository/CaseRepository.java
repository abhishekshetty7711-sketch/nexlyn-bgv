package com.nexlyn.bgv.cases.internal.repository;

import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<BgvCase, UUID>, JpaSpecificationExecutor<BgvCase> {

    /** Deleted cases behave as if they do not exist. */
    Optional<BgvCase> findByIdAndDeletedAtIsNull(UUID id);

    /** Report IDs are unique across all cases, deleted ones included, so an ID is never reused. */
    Optional<BgvCase> findByReportIdIgnoreCase(String reportId);

    boolean existsByReportIdIgnoreCase(String reportId);

    long countByClientId(UUID clientId);

    /**
     * Moves the case's "last changed" marker without bumping its version (a plain bulk update does not), so
     * editing checks does not make an open section form stale.
     */
    @Modifying(flushAutomatically = true)
    @Query("update BgvCase c set c.updatedAt = :now, c.updatedBy = :by where c.id = :id")
    void touch(@Param("id") UUID id, @Param("now") Instant now, @Param("by") UUID by);
}
