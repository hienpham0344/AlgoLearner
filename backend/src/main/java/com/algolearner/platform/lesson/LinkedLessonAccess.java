package com.algolearner.platform.lesson;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Availability overlay; callers acquire the classroom lock before learner mutations. */
public final class LinkedLessonAccess {
    private LinkedLessonAccess() { }

    public static boolean available(JdbcTemplate jdbc, LinkedItemType type, UUID item) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NOT EXISTS(SELECT 1 FROM tbl_lesson_linked_item WHERE item_type=? AND item_id=?)
                    OR EXISTS(SELECT 1 FROM tbl_lesson_linked_item li JOIN tbl_lesson l ON l.id=li.lesson_id
                              WHERE li.item_type=? AND li.item_id=? AND l.status='PUBLISHED')
                """,Boolean.class,type.name(),item,type.name(),item));
    }

    public static List<UUID> hiddenItems(JdbcTemplate jdbc, UUID classroom, LinkedItemType type) {
        return jdbc.query("""
                SELECT li.item_id FROM tbl_lesson_linked_item li JOIN tbl_lesson l ON l.id=li.lesson_id
                WHERE l.classroom_id=? AND li.item_type=? GROUP BY li.item_id
                HAVING NOT bool_or(l.status='PUBLISHED')
                """,(rs,n)->rs.getObject(1,UUID.class),classroom,type.name());
    }
}
