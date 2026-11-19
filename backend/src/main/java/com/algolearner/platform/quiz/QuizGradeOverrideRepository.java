package com.algolearner.platform.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface QuizGradeOverrideRepository extends JpaRepository<QuizGradeOverrideEntity, UUID> {
    org.springframework.data.domain.Page<QuizGradeOverrideEntity> findByQuizIdAndStudentId(UUID quizId, UUID studentId, org.springframework.data.domain.Pageable pageable);
    Optional<QuizGradeOverrideEntity> findFirstByQuizIdAndStudentIdOrderByRevisionDesc(UUID quizId, UUID studentId);
}
