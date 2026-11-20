package com.algolearner.platform.lesson;

import java.util.List;
import java.util.UUID;

/** Module implementations aggregate official attempts; unpublished grades must never satisfy score conditions. */
public interface LessonProgressProvider {
    record ClassroomItemSummary(UUID id, String title, boolean availableToStudent) { }

    LinkedItemType itemType();
    boolean existsInClassroom(UUID itemId, UUID classroomId);
    LessonProgressSource progress(UUID itemId, UUID classroomId, UUID studentId);
    default boolean availableToStudent(UUID itemId, UUID classroomId, UUID studentId) { return false; }
    default List<ClassroomItemSummary> classroomItems(UUID classroomId) { return List.of(); }
}
