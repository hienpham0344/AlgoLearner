package com.algolearner.platform.auth;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {
    @Query("select s.account.id from AuthSessionEntity s where s.refreshHash = :hash")
    Optional<UUID> findAccountIdByRefreshHash(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSessionEntity s where s.refreshHash = :hash")
    Optional<AuthSessionEntity> findForUpdateByHash(@Param("hash") String hash);
    List<AuthSessionEntity> findByAccountIdAndRevokedAtIsNull(UUID accountId);
    boolean existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(UUID id, UUID accountId, Instant now);
}
