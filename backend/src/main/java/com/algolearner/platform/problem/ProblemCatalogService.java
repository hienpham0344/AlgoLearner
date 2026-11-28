package com.algolearner.platform.problem;

import static com.algolearner.platform.problem.ProblemDtos.*;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.common.dto.PageResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemCatalogService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ProblemService problems;
    private final AuditService audit;

    public PageResponse<CatalogItem> browse(
            UUID actor, String q, Difficulty difficulty, String tag, Order order, Pageable page) {
        return list(actor, q, difficulty, tag, order, false, page);
    }

    public PageResponse<CatalogItem> saved(UUID actor, Pageable page) {
        return list(actor, null, null, null, Order.NEWEST, true, page);
    }

    private PageResponse<CatalogItem> list(
            UUID actor,
            String q,
            Difficulty difficulty,
            String tag,
            Order order,
            boolean saved,
            Pageable page) {
        problems.requireRole(actor, "STUDENT", "TEACHER");
        if ((q != null && q.length() > 255) || (tag != null && tag.length() > 80))
            throw ProblemService.failure(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_LENGTH");
        String from =
                " FROM tbl_public_problem p JOIN tbl_problem_revision r ON"
                        + " r.id=p.current_revision_id ";
        String where =
                saved
                        ? " WHERE p.status IN ('PUBLISHED','UNPUBLISHED') AND EXISTS (SELECT FROM"
                                + " tbl_saved_problem s WHERE s.problem_id=p.id AND"
                                + " s.account_id=:actor)"
                        : " WHERE p.status='PUBLISHED'";
        var params = new HashMap<String, Object>();
        params.put("actor", actor);
        if (q != null) {
            where += " AND strpos(lower(r.title),lower(:q))>0";
            params.put("q", q.trim());
        }
        if (difficulty != null) {
            where += " AND r.difficulty=:difficulty";
            params.put("difficulty", difficulty.name());
        }
        if (tag != null) {
            where += " AND jsonb_exists(r.tags,:tag)";
            params.put("tag", tag.trim());
        }
        long count = jdbc.queryForObject("SELECT count(*)" + from + where, params, Long.class);
        int size = Math.min(page.getPageSize(), 100);
        params.put("limit", size);
        params.put("offset", (long) page.getPageNumber() * size);
        String sort =
                switch (order) {
                    case DIFFICULTY ->
                            "CASE r.difficulty WHEN 'EASY' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,";
                    case POPULARITY -> "solved_count DESC,";
                    case NEWEST -> "";
                };
        var rows =
                jdbc.query(
                        """
SELECT p.id,r.title,p.status,p.created_at,r.difficulty,ARRAY(SELECT jsonb_array_elements_text(r.tags)) AS tags,
       a.solved_count,coalesce(a.solved,false) AS solved
"""
                                + from
                                + """
LEFT JOIN LATERAL (SELECT count(DISTINCT owner_id) AS solved_count,bool_or(owner_id=:actor) AS solved
FROM vw_public_submission_outcome WHERE resource_id=p.id AND status='ACCEPTED') a ON true
"""
                                + where
                                + " ORDER BY "
                                + sort
                                + "p.created_at DESC,p.id LIMIT :limit OFFSET :offset",
                        params,
                        (rs, n) ->
                                new CatalogItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("title"),
                                        rs.getString("status"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        Difficulty.valueOf(rs.getString("difficulty")),
                                        List.of((String[]) rs.getArray("tags").getArray()),
                                        rs.getLong("solved_count"),
                                        rs.getBoolean("solved")));
        return PageResponse.fromPage(
                new PageImpl<>(rows, PageRequest.of(page.getPageNumber(), size), count));
    }

    @Transactional
    public void save(UUID actor, UUID id) {
        problems.detail(actor, id);
        jdbc.update(
                "INSERT INTO tbl_saved_problem(id,account_id,problem_id)"
                    + " VALUES(:id,:actor,:problem) ON CONFLICT(account_id,problem_id) DO NOTHING",
                Map.of("id", UUID.randomUUID(), "actor", actor, "problem", id));
    }

    @Transactional
    public void unsave(UUID actor, UUID id) {
        problems.requireRole(actor, "STUDENT", "TEACHER");
        jdbc.update(
                "DELETE FROM tbl_saved_problem WHERE account_id=:actor AND problem_id=:id",
                Map.of("actor", actor, "id", id));
    }

    public String editorial(UUID actor, UUID id) {
        problems.requireRole(actor, "STUDENT", "TEACHER");
        var values =
                jdbc.queryForList(
                        "SELECT r.editorial FROM tbl_problem_revision r JOIN tbl_public_problem p"
                                + " ON p.current_revision_id=r.id WHERE p.id=:id AND p.status IN"
                                + " ('PUBLISHED','UNPUBLISHED') AND r.editorial IS NOT NULL",
                        Map.of("id", id),
                        String.class);
        if (values.isEmpty())
            throw ProblemService.failure(HttpStatus.NOT_FOUND, "EDITORIAL_NOT_FOUND");
        return values.getFirst();
    }

    @Transactional
    public UUID report(UUID actor, UUID id, ReportRequest request) {
        problems.detail(actor, id);
        UUID reportId = UUID.randomUUID();
        var params = Map.of("actor", actor, "problem", id);
        jdbc.queryForList("SELECT id FROM tbl_account WHERE id=:actor FOR UPDATE", params);
        long recent =
                jdbc.queryForObject(
                        "SELECT count(*) FROM tbl_problem_report WHERE reporter_id=:actor AND"
                            + " created_at>clock_timestamp()-INTERVAL '1 minute'",
                        params,
                        Long.class);
        long open =
                jdbc.queryForObject(
                        "SELECT count(*) FROM tbl_problem_report WHERE reporter_id=:actor AND"
                            + " status IN ('OPEN','IN_REVIEW')",
                        params,
                        Long.class);
        if (recent >= 5 || open >= 10)
            throw ProblemService.failure(HttpStatus.TOO_MANY_REQUESTS, "REPORT_RATE_LIMIT");
        long duplicate =
                jdbc.queryForObject(
                        "SELECT count(*) FROM tbl_problem_report WHERE reporter_id=:actor AND"
                            + " problem_id=:problem AND status IN ('OPEN','IN_REVIEW')",
                        params,
                        Long.class);
        if (duplicate > 0) throw ProblemService.failure(HttpStatus.CONFLICT, "REPORT_ALREADY_OPEN");
        jdbc.update(
                "INSERT INTO tbl_problem_report(id,reporter_id,problem_id,reason,description)"
                        + " VALUES(:id,:actor,:problem,:reason,:description)",
                Map.of(
                        "id",
                        reportId,
                        "actor",
                        actor,
                        "problem",
                        id,
                        "reason",
                        request.reason().name(),
                        "description",
                        request.description().trim()));
        return reportId;
    }

    public PageResponse<Report> reports(UUID admin, Pageable page) {
        problems.requireRole(admin, "ADMIN");
        int size = Math.min(page.getPageSize(), 100);
        var params = Map.of("limit", size, "offset", (long) page.getPageNumber() * size);
        var rows =
                jdbc.query(
                        "SELECT"
                            + " id,problem_id,reporter_id,reason,description,status,created_at,image_type"
                            + " IS NOT NULL AS has_image FROM tbl_problem_report ORDER BY"
                            + " created_at DESC,id LIMIT :limit OFFSET :offset",
                        params,
                        (rs, n) ->
                                new Report(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("problem_id", UUID.class),
                                        rs.getObject("reporter_id", UUID.class),
                                        ReportReason.valueOf(rs.getString("reason")),
                                        rs.getString("description"),
                                        ReportStatus.valueOf(rs.getString("status")),
                                        rs.getBoolean("has_image"),
                                        rs.getTimestamp("created_at").toInstant()));
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM tbl_problem_report", Map.of(), Long.class);
        return PageResponse.fromPage(
                new PageImpl<>(rows, PageRequest.of(page.getPageNumber(), size), count));
    }

    @Transactional
    public void updateReport(UUID admin, UUID id, ReportStatus status) {
        problems.requireRole(admin, "ADMIN");
        var rows =
                jdbc.queryForList(
                        "SELECT status FROM tbl_problem_report WHERE id=:id FOR UPDATE",
                        Map.of("id", id),
                        String.class);
        if (rows.isEmpty()) throw ProblemService.failure(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND");
        String before = rows.getFirst();
        if (before.equals(status.name())) return;
        if (before.equals("RESOLVED") || before.equals("REJECTED") || status == ReportStatus.OPEN)
            throw ProblemService.failure(HttpStatus.CONFLICT, "INVALID_REPORT_TRANSITION");
        if (jdbc.update(
                        "UPDATE tbl_problem_report SET status=:status,updated_at=CURRENT_TIMESTAMP"
                                + " WHERE id=:id",
                        Map.of("id", id, "status", status.name()))
                == 0) throw ProblemService.failure(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND");
        audit.record(
                admin,
                "ADMIN",
                "PROBLEM_REPORT",
                id.toString(),
                "PROBLEM_REPORT_STATUS_CHANGED",
                before,
                status.name(),
                null,
                null);
    }
}
