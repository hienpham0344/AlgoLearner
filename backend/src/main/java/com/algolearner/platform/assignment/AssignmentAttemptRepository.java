package com.algolearner.platform.assignment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentAttemptRepository extends JpaRepository<AssignmentAttemptEntity, UUID> {

    @EntityGraph(attributePaths = {"answers", "student"})
    List<AssignmentAttemptEntity> findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(UUID assignmentId, UUID studentId);

    @EntityGraph(attributePaths = {"answers"})
    Optional<AssignmentAttemptEntity> findFirstByAssignmentIdAndStudentIdAndStatus(UUID assignmentId, UUID studentId, AttemptStatus status);

    @EntityGraph(attributePaths = {"student"})
    List<AssignmentAttemptEntity> findByAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);

    @Query(value="""
            SELECT t.id FROM tbl_assignment_attempt t
            WHERE t.submitted_at IS NOT NULL AND t.status IN ('SUBMITTED','GRADED')
              AND EXISTS(SELECT 1 FROM tbl_assignment_code_job c JOIN tbl_judge_job j ON j.id=c.job_id
                WHERE c.attempt_id=t.id AND c.applied_at IS NULL
                  AND j.status NOT IN ('QUEUED','RUNNING','INFRASTRUCTURE_ERROR')
                  AND NOT EXISTS(SELECT 1 FROM tbl_assignment_code_job newer
                    WHERE newer.attempt_id=c.attempt_id AND newer.question_id=c.question_id AND newer.generation>c.generation))
            ORDER BY t.submitted_at,t.id LIMIT 100
            """,nativeQuery=true)
    List<UUID> findPendingOfficialAttemptIds();

    @Query("""
            select distinct a.student.id from AssignmentAttemptEntity a
            where a.assignment.id = :assignmentId and a.submittedAt is not null
            """)
    List<UUID> findStudentsWithSubmissions(@Param("assignmentId") UUID assignmentId);

    long countByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

    boolean existsByAssignmentId(UUID assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AssignmentAttemptEntity a where a.id = :id")
    Optional<AssignmentAttemptEntity> findForUpdateById(@Param("id") UUID id);

    @Query("select a.assignment.id from AssignmentAttemptEntity a where a.id = :id")
    Optional<UUID> findAssignmentIdById(@Param("id") UUID id);

    @Query(value = """
            SELECT t.id FROM tbl_assignment_attempt t
            JOIN tbl_assignment a ON a.id = t.assignment_id
            WHERE t.status = 'IN_PROGRESS'
              AND (COALESCE(t.base_expires_at,CASE WHEN a.duration_minutes>0 THEN t.started_at+a.duration_minutes*INTERVAL '1 minute' END)<=:now
                   OR (NOT a.allow_late_submission AND a.deadline<=:now))
            ORDER BY t.started_at, t.id LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findExpiredTimedAttemptIds(@Param("now") Instant now, @Param("limit") int limit);
}
