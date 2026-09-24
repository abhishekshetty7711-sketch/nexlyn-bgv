package com.nexlyn.bgv.cases.internal.repository;

import com.nexlyn.bgv.cases.internal.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByNameIgnoreCase(String name);

    List<Client> findAllByActiveOrderByNameAsc(boolean active);

    List<Client> findAllByOrderByNameAsc();
}
