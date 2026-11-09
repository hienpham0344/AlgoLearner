package com.algolearner.platform.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccountRestrictionRepository extends JpaRepository<AccountRestrictionEntity, UUID> {

    @Query("SELECT r FROM AccountRestrictionEntity r WHERE r.account.id = :accountId AND r.revokedAt IS NULL AND r.startsAt <= :now AND (r.endsAt IS NULL OR r.endsAt > :now)")
    List<AccountRestrictionEntity> findActiveRestrictions(@Param("accountId") UUID accountId, @Param("now") Instant now);

    @Query("SELECT r FROM AccountRestrictionEntity r WHERE r.account.id = :accountId AND r.kind = :kind AND r.revokedAt IS NULL AND r.startsAt <= :now AND (r.endsAt IS NULL OR r.endsAt > :now)")
    List<AccountRestrictionEntity> findActiveRestrictionsByKind(
            @Param("accountId") UUID accountId,
            @Param("kind") RestrictionKind kind,
            @Param("now") Instant now
    );
}
