package com.algolearner.platform.assignment;

import static com.algolearner.platform.grading.GradeSourceResult.Status.*;

import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import com.algolearner.platform.grading.GradeSourceResult;
import com.algolearner.platform.grading.GradeSourceResult.Audience;
import com.algolearner.platform.grading.GradeSourceResult.Progress;
import com.algolearner.platform.grading.GradeSourceResult.Provenance;
import com.algolearner.platform.grading.GradeSourceResult.SnapshotKind;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
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
public class AssignmentGradeSource {
    private final NamedParameterJdbcTemplate jdbc;

    public List<GradeSourceResult> load(UUID classroomId, Collection<UUID> itemIds,
                                       Collection<UUID> studentIds, Audience audience) {
        if (itemIds.isEmpty() || studentIds.isEmpty()) return List.of();
        boolean teacher = audience == Audience.TEACHER;
        var attempts = jdbc.query("""
                SELECT a.id AS item_id, m.student_id, a.type='GRADED' AS graded, a.scoring_policy,
                       t.id AS attempt_id, t.is_late, e.id AS evaluation_id, t.revision_id,
                       CASE WHEN e.id IS NOT NULL THEN e.final_score
                            WHEN t.current_evaluation_id IS NULL AND t.status='GRADED'
                                 AND (:teacher OR a.is_grade_published) THEN t.final_score END AS earned,
                       CASE WHEN t.snapshot_provenance='TRUSTED'
                                 AND jsonb_typeof(t.revision_snapshot->'questions')='array'
                            THEN (SELECT COALESCE(SUM((q->>'points')::numeric),0)
                                  FROM jsonb_array_elements(t.revision_snapshot->'questions') q)
                       END AS possible,
                       CASE WHEN e.id IS NOT NULL THEN e.id=t.published_evaluation_id
                            ELSE t.current_evaluation_id IS NULL AND a.is_grade_published END AS published,
                       :teacher AND EXISTS (
                           SELECT 1 FROM tbl_assignment_code_job c
                           JOIN tbl_judge_job j ON j.id=c.job_id AND j.mode='SUBMIT'
                           WHERE c.attempt_id=t.id AND c.generation>1 AND c.applied_at IS NULL
                             AND j.status<>'INFRASTRUCTURE_ERROR'
                             AND NOT EXISTS (
                                 SELECT 1 FROM tbl_assignment_code_job newer
                                 WHERE newer.attempt_id=c.attempt_id AND newer.question_id=c.question_id
                                   AND newer.generation>c.generation)
                       ) AS rejudge_pending
                FROM tbl_assignment a
                JOIN tbl_classroom_membership m ON m.classroom_id=a.classroom_id AND m.status='ACTIVE'
                LEFT JOIN tbl_assignment_attempt t ON t.assignment_id=a.id AND t.student_id=m.student_id
                    AND t.submitted_at IS NOT NULL AND t.status IN ('SUBMITTED','GRADED')
                LEFT JOIN LATERAL (
                    SELECT ev.id, ev.final_score FROM tbl_assignment_evaluation ev
                    WHERE ev.attempt_id=t.id AND ev.complete AND ev.final_score IS NOT NULL
                        AND (:teacher OR ev.id=t.published_evaluation_id)
                    ORDER BY ev.generation DESC LIMIT 1
                ) e ON true
                WHERE a.classroom_id=:classroomId AND a.id IN (:itemIds) AND m.student_id IN (:studentIds)
                    AND (:teacher OR (a.status IN ('PUBLISHED','CLOSED')
                        AND (a.start_time IS NULL OR a.start_time<=CURRENT_TIMESTAMP)
                        AND (NOT EXISTS (SELECT 1 FROM tbl_lesson_linked_item li
                            WHERE li.item_type='ASSIGNMENT' AND li.item_id=a.id)
                            OR EXISTS (SELECT 1 FROM tbl_lesson_linked_item li
                                JOIN tbl_lesson l ON l.id=li.lesson_id AND l.classroom_id=a.classroom_id
                                WHERE li.item_type='ASSIGNMENT' AND li.item_id=a.id AND l.status='PUBLISHED'))))
                ORDER BY a.id,m.student_id,t.attempt_number DESC
                """, Map.of("classroomId", classroomId, "itemIds", itemIds, "studentIds", studentIds, "teacher", teacher),
                (rs, row) -> new Attempt(new Cell(rs.getObject("item_id", UUID.class), rs.getObject("student_id", UUID.class)),
                        rs.getBoolean("graded"), ScoringPolicy.valueOf(rs.getString("scoring_policy")),
                        rs.getObject("attempt_id", UUID.class), rs.getBoolean("is_late"),
                        rs.getObject("evaluation_id", UUID.class), rs.getObject("revision_id", UUID.class),
                        rs.getBigDecimal("earned"), rs.getBigDecimal("possible"),
                        rs.getBoolean("published"), rs.getBoolean("rejudge_pending")));
        var cells = new LinkedHashMap<Cell, Grades>();
        for (var attempt : attempts) {
            cells.computeIfAbsent(attempt.cell(), key -> new Grades(attempt.graded())).add(attempt);
        }
        return cells.entrySet().stream()
                .filter(entry -> teacher || entry.getValue().selected != null)
                .map(entry -> entry.getValue().result(entry.getKey(), teacher))
                .toList();
    }

    private record Cell(UUID item, UUID student) { }
    private record Attempt(Cell cell, boolean graded, ScoringPolicy policy, UUID id, boolean late,
                           UUID evaluation, UUID revision, BigDecimal earned, BigDecimal possible,
                           boolean published, boolean rejudgePending) { }

    private static final class Grades {
        private final boolean graded;
        private Attempt selected;
        private int submitted;
        private int late;
        private boolean rejudgePending;

        private Grades(boolean graded) {
            this.graded = graded;
        }

        private void add(Attempt attempt) {
            if (attempt.id() == null) return;
            submitted++;
            if (attempt.late()) late++;
            rejudgePending |= attempt.rejudgePending();
            if (graded && attempt.earned() != null && AssignmentGradeSelection.prefers(attempt.policy(),
                    attempt.earned(), attempt.possible(), selected == null ? null : selected.earned(),
                    selected == null ? null : selected.possible())) {
                selected = attempt;
            }
        }

        private GradeSourceResult result(Cell key, boolean teacher) {
            var status = !graded ? UNGRADED : selected != null ? GRADED : submitted == 0 ? NOT_SUBMITTED : PENDING;
            var progress = teacher ? new Progress(submitted > 0, submitted, late) : null;
            if (selected == null) {
                return new GradeSourceResult(SourceType.ASSIGNMENT, key.item(), key.student(), null, null, status,
                        false, null, rejudgePending, progress);
            }
            var snapshotKind = selected.possible() == null ? SnapshotKind.LEGACY_MISSING : SnapshotKind.ASSIGNMENT_ATTEMPT;
            var provenance = new Provenance(selected.id(), selected.evaluation(), null, selected.revision(), snapshotKind);
            return new GradeSourceResult(SourceType.ASSIGNMENT, key.item(), key.student(), selected.earned(),
                    selected.possible(), status, selected.published(), provenance, rejudgePending, progress);
        }
    }
}
