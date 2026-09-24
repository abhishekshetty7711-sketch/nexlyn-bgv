package com.nexlyn.bgv.auth.internal.repository;

import com.nexlyn.bgv.auth.internal.domain.TotpSecret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TotpSecretRepository extends JpaRepository<TotpSecret, UUID> {
}
