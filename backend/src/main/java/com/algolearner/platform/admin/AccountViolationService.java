package com.algolearner.platform.admin;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.common.exception.ResourceNotFoundException;
import com.algolearner.platform.common.security.AccountAccess;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@Transactional(readOnly=true)
public class AccountViolationService {
    public record View(UUID violationId,UUID accountId,String reason,Instant confirmedAt,Instant revokedAt) { }
    private final JdbcTemplate jdbc;
    private final AccountAccess access;
    private final AuditService audit;
    private final Clock clock;
    public AccountViolationService(JdbcTemplate jdbc,AccountAccess access,AuditService audit,Clock clock) {
        this.jdbc=jdbc;this.access=access;this.audit=audit;this.clock=clock;
    }
    @Transactional
    public View confirm(UUID actor,UUID target,UUID requestId,String reason) {
        requireAdmin(actor);
        if(requestId==null) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        String clean=reason(reason);
        var previous=jdbc.query("SELECT * FROM tbl_account_violation WHERE confirmed_by=? AND request_id=?",this::view,actor,requestId);
        if(!previous.isEmpty()) {
            var old=previous.getFirst();
            if(!old.accountId().equals(target)||!old.reason().equals(clean)) throw new ApiException(ErrorCode.VALIDATION_FAILED,"Request ID was already used for another confirmation");
            return old;
        }
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM tbl_account WHERE id=?)",Boolean.class,target)))
            throw new ResourceNotFoundException("Account",target);
        UUID id=UUID.randomUUID();Instant now=clock.instant();
        jdbc.update("INSERT INTO tbl_account_violation(id,target_account_id,confirmed_by,request_id,reason,confirmed_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                id,target,actor,requestId,clean,Timestamp.from(now),Timestamp.from(now),Timestamp.from(now));
        audit.record(actor,"ADMIN","ACCOUNT",target.toString(),"VIOLATION_CONFIRMED",null,id.toString(),clean,null);
        return new View(id,target,clean,now,null);
    }
    @Transactional
    public View revoke(UUID actor,UUID target,UUID violationId,String reason) {
        requireAdmin(actor);String clean=reason(reason);
        var matches=jdbc.query("SELECT * FROM tbl_account_violation WHERE id=? AND target_account_id=? FOR UPDATE",this::view,violationId,target);
        if(matches.isEmpty()) throw new ResourceNotFoundException("Violation",violationId);
        var old=matches.getFirst();if(old.revokedAt()!=null) return old;
        Instant now=clock.instant();
        jdbc.update("UPDATE tbl_account_violation SET revoked_at=?,revoked_by=?,revoke_reason=?,updated_at=? WHERE id=?",
                Timestamp.from(now),actor,clean,Timestamp.from(now),violationId);
        audit.record(actor,"ADMIN","ACCOUNT",target.toString(),"VIOLATION_REVOKED",violationId.toString(),null,clean,null);
        return new View(old.violationId(),target,old.reason(),old.confirmedAt(),now);
    }
    private void requireAdmin(UUID actor) {
        // Serialize request-ID replay checks and recheck actor authorization under the account lock.
        if(jdbc.queryForList("SELECT id FROM tbl_account WHERE id=? FOR NO KEY UPDATE",UUID.class,actor).isEmpty()
                ||!access.activeRole(actor).equals("ADMIN")) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
    }
    private static String reason(String value) {
        if(value==null||value.isBlank()||value.strip().length()>500) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        return value.strip();
    }
    private View view(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        var revoked=rs.getTimestamp("revoked_at");
        return new View(rs.getObject("id",UUID.class),rs.getObject("target_account_id",UUID.class),rs.getString("reason"),
                rs.getTimestamp("confirmed_at").toInstant(),revoked==null?null:revoked.toInstant());
    }
}
