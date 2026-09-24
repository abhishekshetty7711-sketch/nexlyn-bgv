package com.nexlyn.bgv.documents.internal.repository;

import com.nexlyn.bgv.documents.internal.domain.DocumentKind;
import com.nexlyn.bgv.documents.internal.domain.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every query leaves out retired (soft-deleted) documents. */
public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {

    Optional<StoredDocument> findByIdAndDeletedAtIsNull(UUID id);

    List<StoredDocument> findAllByCheckIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID checkId);

    List<StoredDocument> findAllByCheckIdAndKindAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID checkId, DocumentKind kind);

    long countByCheckIdAndKindAndDeletedAtIsNull(UUID checkId, DocumentKind kind);

    /** Supporting documents per check of one case: rows of {checkId, count}. */
    @Query("select d.checkId, count(d) from StoredDocument d "
            + "where d.caseId = :caseId and d.kind = com.nexlyn.bgv.documents.internal.domain.DocumentKind.CHECK_DOC "
            + "and d.deletedAt is null group by d.checkId")
    List<Object[]> countSupportingByCheck(@Param("caseId") UUID caseId);

    @Query("select max(d.sortOrder) from StoredDocument d where d.checkId = :checkId and d.kind = :kind and d.deletedAt is null")
    Optional<Integer> maxSortOrder(@Param("checkId") UUID checkId, @Param("kind") DocumentKind kind);

    /** Retires every document of a check (its check was deleted). Returns how many. */
    @Modifying(flushAutomatically = true)
    @Query("update StoredDocument d set d.deletedAt = :now, d.updatedAt = :now where d.checkId = :checkId and d.deletedAt is null")
    int retireAllOfCheck(@Param("checkId") UUID checkId, @Param("now") Instant now);
}
