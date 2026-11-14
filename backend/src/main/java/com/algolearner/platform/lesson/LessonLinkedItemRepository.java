package com.algolearner.platform.lesson;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonLinkedItemRepository extends JpaRepository<LessonLinkedItemEntity, UUID> {
    List<LessonLinkedItemEntity> findByLessonIdOrderByDisplayOrderAsc(UUID lessonId);
    boolean existsByItemTypeAndItemId(LinkedItemType itemType, UUID itemId);
    Optional<LessonLinkedItemEntity> findByLessonIdAndItemId(UUID lessonId, UUID itemId);
    long countByLessonId(UUID lessonId);
}
