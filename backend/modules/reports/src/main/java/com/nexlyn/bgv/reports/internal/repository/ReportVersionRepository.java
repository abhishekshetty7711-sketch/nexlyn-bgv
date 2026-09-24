package com.nexlyn.bgv.reports.internal.repository;

import com.nexlyn.bgv.reports.internal.domain.ReportVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportVersionRepository extends JpaRepository<ReportVersion, UUID> {

    List<ReportVersion> findAllByCaseIdOrderByVersionDesc(UUID caseId);

    Optional<ReportVersion> findByCaseIdAndVersion(UUID caseId, int version);

    @Query("select coalesce(max(v.version), 0) from ReportVersion v where v.caseId = :caseId")
    int latestVersion(@Param("caseId") UUID caseId);
}
