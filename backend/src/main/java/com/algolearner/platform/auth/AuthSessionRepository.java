package com.algolearner.platform.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {

    Optional<AuthSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    List<AuthSessionEntity> findByAccountIdAndRevokedAtIsNullAndExpiresAtAfter(UUID accountId, Instant now);

    @Modifying
    @Query("UPDATE AuthSessionEntity s SET s.revokedAt = :now WHERE s.account.id = :accountId AND s.revokedAt IS NULL")
    int revokeAllByAccountId(@Param("accountId") UUID accountId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE AuthSessionEntity s SET s.revokedAt = :now WHERE s.account.id = :accountId AND s.id <> :excludedSessionId AND s.revokedAt IS NULL")
    int revokeOtherSessions(@Param("accountId") UUID accountId, @Param("excludedSessionId") UUID excludedSessionId, @Param("now") Instant now);
}
