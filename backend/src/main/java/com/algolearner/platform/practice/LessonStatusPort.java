package com.algolearner.platform.practice;

import java.util.Set;
import java.util.UUID;

/**
 * Lesson lookup for module 4.5, which does not exist yet. 4.5 supplies the real adapter (including the check that a
 * lesson belongs to the same classroom) and then adds the {@code lesson_id} foreign key.
 */
public interface LessonStatusPort {
    /** True when the lesson has been archived, which hides every practice attached to it. */
    boolean isArchived(UUID lessonId);

    /** Ids of the archived lessons of one classroom, so listings can filter in SQL instead of after paging. */
    Set<UUID> archivedLessonIds(UUID classroomId);
}
