package com.nexlyn.bgv.auth.internal.repository;

import com.nexlyn.bgv.auth.internal.domain.Invitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    /** Locked so one link cannot be accepted twice by two simultaneous requests. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.tokenHash = :hash")
    Optional<Invitation> findForUpdateByTokenHash(@Param("hash") String hash);

    @Query("select i from Invitation i where i.acceptedAt is null and i.expiresAt > :now order by i.createdAt desc")
    List<Invitation> findPending(@Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update Invitation i set i.expiresAt = :now"
            + " where lower(i.email) = lower(:email) and i.acceptedAt is null and i.expiresAt > :now")
    int expirePendingForEmail(@Param("email") String email, @Param("now") Instant now);
}
