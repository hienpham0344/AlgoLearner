package com.algolearner.platform.judge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface RejudgeHistoryRepository extends JpaRepository<RejudgeHistoryEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from RejudgeHistoryEntity h where h.id = :id")
    Optional<RejudgeHistoryEntity> findByIdForUpdate(@Param("id") long id);

    @Query("select h.id from RejudgeHistoryEntity h where h.status = 'QUEUED' order by h.requestedAt")
    List<Long> findQueuedIds(Pageable pageable);
}
