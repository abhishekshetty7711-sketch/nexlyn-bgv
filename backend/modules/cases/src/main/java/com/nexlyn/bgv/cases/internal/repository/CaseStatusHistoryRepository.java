package com.nexlyn.bgv.cases.internal.repository;

import com.nexlyn.bgv.cases.internal.domain.CaseStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseStatusHistoryRepository extends JpaRepository<CaseStatusHistory, UUID> {

    List<CaseStatusHistory> findAllByCaseIdOrderByAtAscIdAsc(UUID caseId);
}
