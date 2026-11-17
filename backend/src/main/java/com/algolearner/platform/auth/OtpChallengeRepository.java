package com.algolearner.platform.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallengeEntity, UUID> {

    @Modifying
    @Query("UPDATE OtpChallengeEntity o SET o.consumedAt = :now WHERE o.account.id = :accountId AND o.purpose = :purpose AND o.consumedAt IS NULL")
    int consumeOutstanding(@Param("accountId") UUID accountId, @Param("purpose") OtpPurpose purpose,
            @Param("now") Instant now);

    @Query("SELECT o FROM OtpChallengeEntity o WHERE o.account.id = :accountId AND o.purpose = :purpose ORDER BY o.sentAt DESC LIMIT 1")
    Optional<OtpChallengeEntity> findLatestByAccountAndPurpose(
            @Param("accountId") UUID accountId,
            @Param("purpose") OtpPurpose purpose
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OtpChallengeEntity o WHERE o.account.id = :accountId AND o.purpose = :purpose AND o.consumedAt IS NULL AND o.expiresAt > :now ORDER BY o.sentAt DESC LIMIT 1")
    Optional<OtpChallengeEntity> findActiveByAccountAndPurpose(
            @Param("accountId") UUID accountId,
            @Param("purpose") OtpPurpose purpose,
            @Param("now") Instant now
    );
}

