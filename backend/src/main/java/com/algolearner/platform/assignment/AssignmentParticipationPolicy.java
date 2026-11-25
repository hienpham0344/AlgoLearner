package com.algolearner.platform.assignment;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The assignment aggregate lock serializes participation changes and starts. */
@Service
@Profile("!mock")
@Transactional(readOnly = true)
public class AssignmentParticipationPolicy {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;
    public AssignmentParticipationPolicy(JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.audit = audit; this.clock = clock;
    }
    record Allowance(int extra, long baseline, java.time.Instant unlockedAt) { }
    Allowance allowance(UUID assignment, UUID student) {
        return jdbc.query("SELECT extra_attempts, reset_baseline, unlocked_at FROM tbl_assignment_participation WHERE assignment_id=? AND student_id=?",
                (r, n) -> new Allowance(r.getInt(1), r.getLong(2), r.getTimestamp(3).toInstant()), assignment, student)
                .stream().findFirst().orElse(new Allowance(0, 0, java.time.Instant.EPOCH));
    }
    @Transactional
    public void grant(UUID teacher, UUID assignment, UUID student, int extra, boolean reset, String reason, long officialCount) {
        if (extra < 0 || (!reset && extra == 0) || reason == null || reason.isBlank())
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        var before = allowance(assignment, student);
        int nextExtra = reset ? extra : Math.addExact(before.extra(), extra);
        long baseline = reset ? officialCount : before.baseline();
        jdbc.update("""
                INSERT INTO tbl_assignment_participation(assignment_id, student_id, extra_attempts, reset_baseline, unlocked_at)
                VALUES (?,?,?,?,?) ON CONFLICT(assignment_id,student_id) DO UPDATE SET extra_attempts=EXCLUDED.extra_attempts,
                reset_baseline=EXCLUDED.reset_baseline, unlocked_at=EXCLUDED.unlocked_at, updated_at=EXCLUDED.unlocked_at
                """, assignment, student, nextExtra, baseline, Timestamp.from(clock.instant()));
        audit.record(teacher, "TEACHER", "ASSIGNMENT_PARTICIPATION", assignment + ":" + student,
                reset ? "RESET_ATTEMPT_COUNT" : "GIVE_EXTRA_ATTEMPT", before.toString(),
                new Allowance(nextExtra, baseline, clock.instant()).toString(), reason, null);
    }
}
