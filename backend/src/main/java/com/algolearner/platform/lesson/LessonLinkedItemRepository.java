package com.algolearner.platform.lesson;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonLinkedItemRepository extends JpaRepository<LessonLinkedItemEntity, UUID> {
    List<LessonLinkedItemEntity> findByLessonIdOrderByDisplayOrderAsc(UUID lessonId);
    boolean existsByItemTypeAndItemId(LinkedItemType itemType, UUID itemId);
    boolean existsByItemTypeAndItemIdAndLessonStatus(LinkedItemType itemType, UUID itemId, LessonStatus status);
    Optional<LessonLinkedItemEntity> findByLessonIdAndItemId(UUID lessonId, UUID itemId);
    boolean existsByLessonIdAndItemTypeAndItemId(UUID lessonId, LinkedItemType type, UUID itemId);
    List<LessonLinkedItemEntity> findByItemTypeAndItemId(LinkedItemType type, UUID itemId);
    @Query("select li from LessonLinkedItemEntity li join fetch li.lesson where li.lesson.classroom.id = :classroomId")
    List<LessonLinkedItemEntity> findByLessonClassroomId(@Param("classroomId") UUID classroomId);
    long countByLessonId(UUID lessonId);
}
