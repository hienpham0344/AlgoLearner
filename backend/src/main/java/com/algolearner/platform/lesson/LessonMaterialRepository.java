package com.algolearner.platform.lesson;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonMaterialRepository extends JpaRepository<LessonMaterialEntity, UUID> {
    List<LessonMaterialEntity> findByLessonId(UUID lessonId);
    Optional<LessonMaterialEntity> findByIdAndLessonId(UUID id, UUID lessonId);
}
