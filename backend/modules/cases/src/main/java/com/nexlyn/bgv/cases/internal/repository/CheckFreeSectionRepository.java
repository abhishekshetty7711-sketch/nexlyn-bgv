package com.nexlyn.bgv.cases.internal.repository;

import com.nexlyn.bgv.cases.internal.domain.CheckFreeSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckFreeSectionRepository extends JpaRepository<CheckFreeSection, UUID> {

    List<CheckFreeSection> findAllByCheckIdOrderBySortOrderAsc(UUID checkId);

    Optional<CheckFreeSection> findByIdAndCheckId(UUID id, UUID checkId);

    void deleteAllByCheckId(UUID checkId);
}
