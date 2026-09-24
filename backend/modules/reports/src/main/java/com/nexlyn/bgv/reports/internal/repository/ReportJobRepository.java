package com.nexlyn.bgv.reports.internal.repository;

import com.nexlyn.bgv.reports.internal.domain.ReportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {

    Optional<ReportJob> findByIdAndCaseId(UUID id, UUID caseId);

    /** Jobs that were waiting or running when the server stopped can never finish: mark them failed. */
    @Modifying
    @Query("update ReportJob j set j.status = com.nexlyn.bgv.reports.internal.domain.ReportJob.Status.FAILED, "
            + "j.error = 'The server restarted before this report was finished. Please generate it again.', j.finishedAt = :now "
            + "where j.status in (com.nexlyn.bgv.reports.internal.domain.ReportJob.Status.QUEUED, com.nexlyn.bgv.reports.internal.domain.ReportJob.Status.RUNNING)")
    int failUnfinished(@Param("now") Instant now);

    long countByCaseIdAndStatusIn(UUID caseId, java.util.Collection<ReportJob.Status> statuses);
}
