package com.nexlyn.bgv.cases.internal.repository;

import com.nexlyn.bgv.cases.internal.domain.CheckDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CheckDetailRepository extends JpaRepository<CheckDetail, UUID> {

    List<CheckDetail> findAllByCheckIdOrderBySortOrderAsc(UUID checkId);

    void deleteAllByCheckId(UUID checkId);
}
