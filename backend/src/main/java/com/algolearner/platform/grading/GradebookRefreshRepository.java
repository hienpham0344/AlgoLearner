package com.algolearner.platform.grading;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mock")
@RequiredArgsConstructor
public class GradebookRefreshRepository {
    record Work(UUID classroomId, UUID gradebookId, UUID studentId) { }
    record Claimed(Work work, int attempts, OffsetDateTime updatedAt) { }
    private final JdbcTemplate jdbc;
    private static final String ENQUEUE_CONFLICT = """
            ON CONFLICT (gradebook_id,student_id) DO UPDATE SET
                updated_at=clock_timestamp(),
                attempt_count=CASE WHEN queued.failed_at IS NOT NULL THEN 0 ELSE queued.attempt_count END,
                next_attempt_at=CASE WHEN queued.failed_at IS NOT NULL THEN clock_timestamp() ELSE queued.next_attempt_at END,
                last_error=CASE WHEN queued.failed_at IS NOT NULL THEN NULL ELSE queued.last_error END,
                failed_at=NULL
            """;

    void enqueue(GradeSourceChanged event) {
        jdbc.update("""
                INSERT INTO tbl_gradebook_refresh AS queued(gradebook_id,student_id)
                SELECT b.id,p.student_id FROM tbl_gradebook b
                JOIN tbl_gradebook_published_student p ON p.gradebook_id=b.id
                WHERE b.classroom_id=? AND p.student_id=?
                """ + ENQUEUE_CONFLICT, event.classroomId(), event.studentId());
    }

    void enqueueStaleSnapshot(UUID gradebook, UUID student, UUID result) {
        jdbc.update("""
                INSERT INTO tbl_gradebook_refresh AS queued(gradebook_id,student_id)
                SELECT gradebook_id,student_id FROM tbl_gradebook_published_student
                WHERE gradebook_id=? AND student_id=? AND result_id=?
                """ + ENQUEUE_CONFLICT + "WHERE queued.failed_at IS NOT NULL", gradebook, student, result);
    }

    List<Work> due(int limit) {
        return jdbc.query("""
                SELECT b.classroom_id,r.gradebook_id,r.student_id FROM tbl_gradebook_refresh r
                JOIN tbl_gradebook b ON b.id=r.gradebook_id
                WHERE r.failed_at IS NULL AND r.next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY r.next_attempt_at,r.created_at,r.gradebook_id,r.student_id LIMIT ?
                """, (r, n) -> new Work(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getObject(3, UUID.class)), limit);
    }

    Optional<Claimed> claim(Work requested) {
        // Lock the classroom in the due-job query to preserve classroom â†’ gradebook â†’ queue order.
        var filter = requested == null ? "" : " AND r.gradebook_id=? AND r.student_id=?";
        var args = requested == null ? new Object[0] : new Object[]{requested.gradebookId(), requested.studentId()};
        return jdbc.query("""
                SELECT b.classroom_id,r.gradebook_id,r.student_id,r.attempt_count,r.updated_at
                FROM tbl_gradebook_refresh r JOIN tbl_gradebook b ON b.id=r.gradebook_id
                JOIN tbl_classroom c ON c.id=b.classroom_id
                WHERE r.failed_at IS NULL AND r.next_attempt_at <= CURRENT_TIMESTAMP
                """ + filter + "\n" + """
                ORDER BY r.next_attempt_at,r.created_at,r.gradebook_id,r.student_id
                LIMIT 1 FOR UPDATE OF c SKIP LOCKED
                """, (r, n) -> new Claimed(new Work(r.getObject(1, UUID.class), r.getObject(2, UUID.class),
                        r.getObject(3, UUID.class)), r.getInt(4), r.getObject(5, OffsetDateTime.class)), args)
                .stream().findFirst();
    }

    boolean lockWork(Claimed claimed) {
        var work = claimed.work();
        return !jdbc.query("""
                SELECT student_id FROM tbl_gradebook_refresh
                WHERE gradebook_id=? AND student_id=? AND updated_at=? AND attempt_count=?
                    AND failed_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
                FOR UPDATE SKIP LOCKED
                """, (r, n) -> r.getObject(1, UUID.class), work.gradebookId(), work.studentId(),
                claimed.updatedAt(), claimed.attempts()).isEmpty();
    }

    boolean recordFailure(Claimed claimed, int delaySeconds, boolean terminal) {
        var work = claimed.work();
        return jdbc.update("""
                UPDATE tbl_gradebook_refresh SET attempt_count=attempt_count+1,
                    next_attempt_at=CASE WHEN ? THEN NULL ELSE clock_timestamp()+?*interval '1 second' END,
                    failed_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,
                    last_error='GRADEBOOK_REFRESH_PROCESSING_FAILED',updated_at=clock_timestamp()
                WHERE gradebook_id=? AND student_id=? AND updated_at=? AND attempt_count=? AND failed_at IS NULL
                """, terminal, delaySeconds, terminal, work.gradebookId(), work.studentId(), claimed.updatedAt(),
                claimed.attempts()) == 1;
    }

    boolean activeMember(Work work) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM tbl_classroom_membership
                    WHERE classroom_id=? AND student_id=? AND status='ACTIVE')
                """, Boolean.class, work.classroomId(), work.studentId()));
    }

    void complete(Work work) {
        jdbc.update("DELETE FROM tbl_gradebook_refresh WHERE gradebook_id=? AND student_id=?",
                work.gradebookId(), work.studentId());
    }
}
