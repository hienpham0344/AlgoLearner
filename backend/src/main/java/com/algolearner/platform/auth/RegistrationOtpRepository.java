package com.algolearner.platform.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtpEntity, UUID> {
    @Query(value = "SELECT * FROM tbl_registration_otp WHERE account_id = :accountId AND purpose = :purpose ORDER BY sent_at DESC LIMIT 1 FOR UPDATE", nativeQuery = true)
    Optional<RegistrationOtpEntity> findFirstByAccountIdAndPurposeOrderBySentAtDesc(@Param("accountId") UUID accountId,
            @Param("purpose") String purpose);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RegistrationOtpEntity o where o.account.id = :accountId and o.resetTokenHash = :resetTokenHash")
    Optional<RegistrationOtpEntity> findByAccountIdAndResetTokenHash(@Param("accountId") UUID accountId,
            @Param("resetTokenHash") String resetTokenHash);
    java.util.List<RegistrationOtpEntity> findByAccountId(UUID accountId);
}
