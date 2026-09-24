package com.nexlyn.bgv.auth.internal.repository;

import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.domain.AdminStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByEmailIgnoreCase(String email);

    /** How many admins with this status hold the permission through any of their roles. */
    @Query("select count(distinct a.id) from Admin a join a.roles r join r.permissions p"
            + " where a.status = :status and p = :permission")
    long countWithPermission(@Param("status") AdminStatus status, @Param("permission") String permission);

    @Query("select distinct a.id from Admin a join a.roles r where r.id = :roleId")
    List<UUID> findIdsByRoleId(@Param("roleId") UUID roleId);

    @Query("select r.id, count(a) from Admin a join a.roles r group by r.id")
    List<Object[]> countMembersPerRole();
}
