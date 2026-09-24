package com.nexlyn.bgv.auth.internal.repository;

import com.nexlyn.bgv.auth.internal.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Locks the row so two simultaneous refreshes with the same token are handled one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findForUpdateByTokenHash(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.adminId = :adminId and t.revokedAt is null")
    int revokeAllForAdmin(@Param("adminId") UUID adminId, @Param("now") Instant now);

    boolean existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(UUID familyId, Instant now);
}
