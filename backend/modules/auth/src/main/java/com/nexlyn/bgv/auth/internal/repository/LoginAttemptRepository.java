package com.nexlyn.bgv.auth.internal.repository;

import com.nexlyn.bgv.auth.internal.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
}
