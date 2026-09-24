package com.nexlyn.bgv.cases.internal.repository;

import com.nexlyn.bgv.cases.internal.domain.CheckField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CheckFieldRepository extends JpaRepository<CheckField, UUID> {

    List<CheckField> findAllByCheckIdOrderBySortOrderAsc(UUID checkId);

    List<CheckField> findAllByCheckIdIn(Collection<UUID> checkIds);

    void deleteAllByCheckId(UUID checkId);
}
