package com.algolearner.platform.judge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionEvaluationRepository extends JpaRepository<SubmissionEvaluationEntity, UUID> {
    Optional<SubmissionEvaluationEntity> findBySubmissionIdAndCurrentOfficialTrue(UUID submissionId);
    long countBySubmissionId(UUID submissionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SubmissionEvaluationEntity e where e.id = :id")
    Optional<SubmissionEvaluationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select e.id from SubmissionEvaluationEntity e where e.triggerType = 'INITIAL' and e.status = 'QUEUED' order by e.createdAt")
    List<UUID> findQueuedInitialIds(Pageable pageable);
}
