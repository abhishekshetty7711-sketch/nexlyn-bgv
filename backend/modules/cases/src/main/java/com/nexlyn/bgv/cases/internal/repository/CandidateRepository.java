package com.nexlyn.bgv.cases.internal.repository;

import com.nexlyn.bgv.cases.internal.domain.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    Optional<Candidate> findByCaseId(UUID caseId);

    List<Candidate> findAllByCaseIdIn(Collection<UUID> caseIds);

    /** Changes only the photo, so an open candidate form (which holds the case version) is not made stale. */
    @Modifying(flushAutomatically = true)
    @Query("update Candidate c set c.photoDocumentId = :documentId where c.caseId = :caseId")
    int setPhoto(@Param("caseId") UUID caseId, @Param("documentId") UUID documentId);
}
