package com.algolearner.platform.quiz;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, UUID> {
    @Query("select a.quiz.id from QuizAttemptEntity a where a.id = :id")
    Optional<UUID> findQuizId(@Param("id") UUID id);
    List<QuizAttemptEntity> findByQuizIdAndStudentIdOrderByNumberAsc(UUID quizId, UUID studentId);
    Page<QuizAttemptEntity> findByQuizId(UUID quizId, Pageable pageable);
    List<QuizAttemptEntity> findByStatusAndDeadlineLessThanEqual(String status, Instant deadline, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from QuizAttemptEntity a where a.id = :id")
    Optional<QuizAttemptEntity> lockById(@Param("id") UUID id);
}
