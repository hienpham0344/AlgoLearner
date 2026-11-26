package com.algolearner.platform.quiz;

import static com.algolearner.platform.grading.GradeSourceResult.Status.*;

import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import com.algolearner.platform.grading.GradeSourceResult;
import com.algolearner.platform.grading.GradeSourceResult.Audience;
import com.algolearner.platform.grading.GradeSourceResult.Progress;
import com.algolearner.platform.grading.GradeSourceResult.Provenance;
import com.algolearner.platform.grading.GradeSourceResult.SnapshotKind;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizGradeSource {
    private static final BigDecimal QUIZ_MAXIMUM = BigDecimal.valueOf(100);
    private final NamedParameterJdbcTemplate jdbc;

    public List<GradeSourceResult> load(UUID classroomId, Collection<UUID> itemIds,
                                       Collection<UUID> studentIds, Audience audience) {
        if (itemIds.isEmpty() || studentIds.isEmpty()) return List.of();
        boolean teacher = audience == Audience.TEACHER;
        // Rank only eligible overrides so an unpublished override cannot change a Student's visible result.
        return jdbc.query("""
                SELECT q.id AS item_id, m.student_id, q.graded,
                       COALESCE(change.attempt_id,best.id) AS attempt_id, change.id AS override_id,
                       COALESCE(change.final_score,best.score) AS earned,
                       COALESCE(change.grade_published,best.grade_published,false) AS published,
                       COALESCE(completion.completed,false) AS completed, counts.submitted, counts.late
                FROM tbl_quiz q
                JOIN tbl_classroom_membership m ON m.classroom_id=q.classroom_id AND m.status='ACTIVE'
                LEFT JOIN tbl_quiz_completion completion ON :teacher AND completion.quiz_id=q.id
                    AND completion.student_id=m.student_id
                LEFT JOIN LATERAL (
                    SELECT count(*) AS submitted, count(*) FILTER (WHERE a.late) AS late
                    FROM tbl_quiz_attempt a
                    WHERE :teacher AND a.quiz_id=q.id AND a.student_id=m.student_id AND a.status='SUBMITTED'
                ) counts ON true
                LEFT JOIN LATERAL (
                    SELECT a.id,a.score,a.grade_published FROM tbl_quiz_attempt a
                    WHERE a.quiz_id=q.id AND a.student_id=m.student_id AND a.status='SUBMITTED'
                        AND a.score IS NOT NULL AND (:teacher OR a.grade_published)
                    ORDER BY a.score DESC,a.grade_published DESC,a.id LIMIT 1
                ) best ON q.graded
                LEFT JOIN LATERAL (
                    SELECT g.id,g.attempt_id,g.final_score,a.grade_published
                    FROM tbl_quiz_grade_override g JOIN tbl_quiz_attempt a ON a.id=g.attempt_id
                        AND a.quiz_id=q.id AND a.student_id=m.student_id AND a.status='SUBMITTED'
                    WHERE g.quiz_id=q.id AND g.student_id=m.student_id AND (:teacher OR a.grade_published)
                    ORDER BY g.id DESC LIMIT 1
                ) change ON best.id IS NOT NULL
                WHERE q.classroom_id=:classroomId AND q.id IN (:itemIds) AND m.student_id IN (:studentIds)
                    AND (:teacher OR (best.id IS NOT NULL AND q.status IN ('PUBLISHED','CLOSED')
                        AND (NOT EXISTS (SELECT 1 FROM tbl_lesson_linked_item li
                            WHERE li.item_type='QUIZ' AND li.item_id=q.id)
                            OR EXISTS (SELECT 1 FROM tbl_lesson_linked_item li
                                JOIN tbl_lesson l ON l.id=li.lesson_id AND l.classroom_id=q.classroom_id
                                WHERE li.item_type='QUIZ' AND li.item_id=q.id AND l.status='PUBLISHED'))))
                ORDER BY q.id,m.student_id
                """, Map.of("classroomId", classroomId, "itemIds", itemIds, "studentIds", studentIds, "teacher", teacher),
                (rs, row) -> {
                    var earned = rs.getBigDecimal("earned");
                    int submitted = rs.getInt("submitted");
                    var status = !rs.getBoolean("graded") ? UNGRADED : earned != null ? GRADED
                            : submitted == 0 ? NOT_SUBMITTED : PENDING;
                    var provenance = earned == null ? null : new Provenance(rs.getObject("attempt_id", UUID.class),
                            null, rs.getObject("override_id", Long.class), null, SnapshotKind.QUIZ_ATTEMPT);
                    var progress = teacher ? new Progress(rs.getBoolean("completed"), submitted, rs.getInt("late")) : null;
                    return new GradeSourceResult(SourceType.QUIZ, rs.getObject("item_id", UUID.class),
                            rs.getObject("student_id", UUID.class), earned, earned == null ? null : QUIZ_MAXIMUM,
                            status, rs.getBoolean("published"), provenance, false, progress);
                });
    }
}
