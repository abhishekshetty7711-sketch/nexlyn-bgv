package com.nexlyn.bgv.auth.internal.repository;

import com.nexlyn.bgv.auth.internal.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);
}
