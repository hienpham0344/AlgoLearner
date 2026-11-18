package com.algolearner.platform.profile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Canonical official public submissions, with the latest valid rejudge verdict authoritative. */
@Repository
@Profile("internal-auth")
@Transactional(readOnly = true)
public class InternalPracticeRepository implements PracticeRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public InternalPracticeRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public List<Accepted> acceptedPublicSubmissions(UUID accountId) {
        return jdbc.query("""
                SELECT s.resource_id,s.created_at FROM tbl_submission s
                JOIN LATERAL (SELECT j.status FROM tbl_judge_job j WHERE j.submission_id=s.id
                              AND j.mode='SUBMIT'
                              AND j.status IN ('ACCEPTED','WRONG_ANSWER','COMPILE_ERROR','RUNTIME_ERROR',
                                               'MEMORY_LIMIT_EXCEEDED','TIME_LIMIT_EXCEEDED','OUTPUT_LIMIT_EXCEEDED')
                              ORDER BY j.generation DESC,j.created_at DESC,j.id LIMIT 1) latest ON true
                WHERE s.owner_id=:account AND s.scope='PUBLIC_PROBLEM' AND latest.status='ACCEPTED'
                ORDER BY s.created_at,s.id
                """,Map.of("account",accountId),(rs,row)->new Accepted(rs.getObject("resource_id",UUID.class),rs.getTimestamp("created_at").toInstant()));
    }
}
