package com.algolearner.platform.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtpEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RegistrationOtpEntity> findFirstByAccountIdAndPurposeOrderBySentAtDesc(UUID accountId, String purpose);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RegistrationOtpEntity> findByAccountIdAndResetTokenHash(UUID accountId, String resetTokenHash);
    java.util.List<RegistrationOtpEntity> findByAccountId(UUID accountId);
}
