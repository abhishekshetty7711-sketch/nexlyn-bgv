package com.nexlyn.bgv.auth.internal.repository;

import com.nexlyn.bgv.auth.internal.domain.BackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BackupCodeRepository extends JpaRepository<BackupCode, UUID> {

    List<BackupCode> findByAdminIdAndUsedAtIsNull(UUID adminId);

    @Modifying(flushAutomatically = true)
    @Query("delete from BackupCode c where c.adminId = :adminId")
    void deleteAllForAdmin(@Param("adminId") UUID adminId);
}
