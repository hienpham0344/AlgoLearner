package com.algolearner.platform.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id = :id")
    Optional<AccountEntity> findForUpdateById(@Param("id") UUID id);
    Optional<AccountEntity> findByUsernameIgnoreCase(String username);
    Optional<AccountEntity> findByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where lower(a.email) = lower(:identity)")
    Optional<AccountEntity> findForLoginByEmail(@Param("identity") String identity);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where lower(a.username) = lower(:identity)")
    Optional<AccountEntity> findForLoginByUsername(@Param("identity") String identity);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where lower(a.email) = lower(:identity) or lower(a.username) = lower(:identity) order by a.id")
    List<AccountEntity> findForIdentityResolution(@Param("identity") String identity);
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(:identity, 0))", nativeQuery = true)
    void lockIdentity(@Param("identity") String identity);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AccountEntity> findByStatusAndSuspensionEndsAtLessThanEqual(AccountStatus status, Instant time);
}
