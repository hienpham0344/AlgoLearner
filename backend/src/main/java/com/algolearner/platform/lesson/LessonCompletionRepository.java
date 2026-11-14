package com.algolearner.platform.lesson;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonCompletionRepository extends JpaRepository<LessonCompletionEntity, UUID> {
    Optional<LessonCompletionEntity> findByLessonIdAndStudentId(UUID lessonId, UUID studentId);
    boolean existsByLessonId(UUID lessonId);
    boolean existsByLessonIdAndIsCompletedTrue(UUID lessonId);
    List<LessonCompletionEntity> findByStudentId(UUID studentId);
}
