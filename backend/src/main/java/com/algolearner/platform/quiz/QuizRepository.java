package com.algolearner.platform.quiz;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<QuizEntity, UUID> {
    Page<QuizEntity> findByClassroomId(UUID classroomId, Pageable pageable);
    Page<QuizEntity> findByClassroomIdAndStatusNot(UUID classroomId, String status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuizEntity q where q.id = :id")
    Optional<QuizEntity> lockById(@Param("id") UUID id);
}
