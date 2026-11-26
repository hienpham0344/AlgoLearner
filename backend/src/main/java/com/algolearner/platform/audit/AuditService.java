package com.algolearner.platform.audit;

import java.time.Clock;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes state summaries atomically with the caller; callers must exclude secrets and source payloads. */
@Service
@Profile("!mock")
public class AuditService {
    public record RequestContext(String clientIp, String userAgent) {
        public static final RequestContext EMPTY = new RequestContext(null, null);
    }
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuditService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String actorRole, String targetType, String targetId, String action,
                       String beforeState, String afterState, String reason, String correlationId) {
        var now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO tbl_audit_log(actor_id, actor_role, target_type, target_id, action,
                    before_state, after_state, reason, correlation_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, actorId, actorRole, targetType, targetId, action, beforeState, afterState,
                reason, correlationId, now, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordGradebook(UUID classroomId, UUID gradebookId, UUID actorId, String action,
                                String beforeState, String afterState, String reason, String metadata,
                                RequestContext request) {
        var now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO tbl_audit_log(actor_id,actor_role,target_type,target_id,action,before_state,after_state,
                    reason,classroom_id,metadata,client_ip,user_agent,created_at,updated_at)
                VALUES (?,?,'GRADEBOOK',?,?,?,?,?,?,?::jsonb,?,?,?,?)
                """, actorId, actorId == null ? "SYSTEM" : "TEACHER", gradebookId.toString(), action, beforeState,
                afterState, reason, classroomId, metadata, bounded(request.clientIp(), 45), bounded(request.userAgent(), 500),
                now, now);
    }

    private static String bounded(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
