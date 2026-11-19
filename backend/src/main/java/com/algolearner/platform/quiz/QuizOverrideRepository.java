package com.algolearner.platform.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface QuizOverrideRepository extends JpaRepository<QuizOverrideEntity, UUID> {
    java.util.List<QuizOverrideEntity> findByQuizId(UUID quizId);
    Optional<QuizOverrideEntity> findByQuizIdAndStudentId(UUID quizId, UUID studentId);
}
