package com.algolearner.platform.assignment;

import com.algolearner.platform.lesson.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads persisted official projections without depending on AssignmentService or LessonService. */
@Component
@Profile("!mock")
@Transactional(readOnly = true)
public class AssignmentLessonProgressProvider implements LessonProgressProvider {
    private final JdbcTemplate jdbc;
    public AssignmentLessonProgressProvider(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public LinkedItemType itemType() { return LinkedItemType.ASSIGNMENT; }
    public boolean existsInClassroom(UUID itemId, UUID classroomId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM tbl_assignment WHERE id=? AND classroom_id=?)", Boolean.class, itemId, classroomId));
    }
    public boolean availableToStudent(UUID itemId, UUID classroomId, UUID studentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM tbl_assignment a WHERE a.id=? AND a.classroom_id=? AND a.status='PUBLISHED'
                AND (a.start_time IS NULL OR a.start_time<=CURRENT_TIMESTAMP)
                AND (NOT EXISTS(SELECT 1 FROM tbl_lesson_linked_item li WHERE li.item_type='ASSIGNMENT' AND li.item_id=a.id)
                OR EXISTS(SELECT 1 FROM tbl_lesson_linked_item li JOIN tbl_lesson l ON l.id=li.lesson_id
                  WHERE li.item_type='ASSIGNMENT' AND li.item_id=a.id AND l.status='PUBLISHED')))
                """, Boolean.class, itemId, classroomId));
    }
    @Override
    public List<ClassroomItemSummary> classroomItems(UUID classroomId) {
        return jdbc.query(
                "SELECT id, title, (status = 'PUBLISHED') AS available FROM tbl_assignment WHERE classroom_id = ? ORDER BY created_at DESC, id",
                (rs, rowNum) -> new ClassroomItemSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3)),
                classroomId);
    }
    private record Result(UUID attempt, UUID evaluation, BigDecimal percent, java.time.Instant submitted, String policy) { }
    public LessonProgressSource progress(UUID itemId, UUID classroomId, UUID studentId) {
        var results = jdbc.query("""
                SELECT t.id,e.id,CASE WHEN m.maximum>0 THEN e.final_score*100/m.maximum END,
                t.submitted_at,a.scoring_policy FROM tbl_assignment_attempt t
                JOIN tbl_assignment a ON a.id=t.assignment_id
                LEFT JOIN tbl_assignment_evaluation e ON e.id=t.published_evaluation_id AND e.complete=true
                LEFT JOIN LATERAL (SELECT sum((q->>'points')::numeric) maximum
                  FROM jsonb_array_elements(t.revision_snapshot->'questions') q) m ON true
                WHERE a.id=? AND a.classroom_id=? AND t.student_id=? AND t.submitted_at IS NOT NULL
                ORDER BY t.submitted_at DESC,t.attempt_number DESC
                """, (r,n) -> new Result(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getBigDecimal(3),
                    r.getTimestamp(4).toInstant(),r.getString(5)),itemId,classroomId,studentId);
        if (results.isEmpty()) return new LessonProgressSource(false,false,null,null,"empty");
        var chosen = results.getFirst();
        if ("HIGHEST".equals(chosen.policy())) {
            for (var result : results) if (result.percent()!=null && (chosen.percent()==null || result.percent().compareTo(chosen.percent())>0)) chosen=result;
        }
        return new LessonProgressSource(true,chosen.evaluation()!=null,chosen.percent()==null?null:chosen.percent().setScale(2,RoundingMode.HALF_UP),
                chosen.evaluation(), chosen.attempt()+":"+chosen.evaluation());
    }
}
