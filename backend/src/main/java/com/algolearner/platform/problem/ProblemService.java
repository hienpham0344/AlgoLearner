package com.algolearner.platform.problem;

import static com.algolearner.platform.judge.JudgeGateway.*;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.judge.JudgeGateway;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.*;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Profile("!mock")
@Transactional(readOnly = true)
public class ProblemService implements PublicProblemCatalog {
    public record Content(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 100000) String statement,
            @NotEmpty Set<Language> languages,
            @NotEmpty List<TestCaseSnapshot> testCases,
            Limits limits,
            Feedback feedback,
            ProblemDtos.Difficulty difficulty,
            @Size(max = 30) Set<@NotBlank @Size(max = 80) String> tags,
            @Size(max = 100000) String editorial) {
        public Content(
                String title,
                String statement,
                Set<Language> languages,
                List<TestCaseSnapshot> testCases,
                Limits limits,
                Feedback feedback) {
            this(title, statement, languages, testCases, limits, feedback, null, Set.of(), null);
        }
    }

    public record CodeInput(
            @NotBlank @Size(max = 65536) String source,
            @NotNull Language language,
            @NotBlank @Size(max = 64) String requestKey) {}

    public record View(
            UUID id,
            UUID revisionId,
            String title,
            String statement,
            Set<Language> languages,
            List<TestCaseSnapshot> samples,
            Limits limits,
            String status) {}

    public record Contribution(
            UUID id,
            String status,
            String reviewReason,
            ContentSnapshot content,
            String publicationStatus) {}

    public record Summary(UUID id, String title, String status, Instant createdAt) {}

    public record Submission(UUID jobId, UUID submissionId, String status, Instant submittedAt) {}

    public record Statistics(
            long solved,
            int currentStreak,
            int highestStreak,
            Map<ProblemDtos.Difficulty, Long> solvedByDifficulty,
            long totalSubmissions,
            double acceptanceRate) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final JudgeGateway judge;
    private final AuditService audit;
    private final Clock clock;
    private final ProblemReviewNotifier notifier;

    public ProblemService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper json,
            JudgeGateway judge,
            AuditService audit,
            Clock clock,
            ProblemReviewNotifier notifier) {
        this.notifier = notifier;
        this.jdbc = jdbc;
        this.json = json;
        this.judge = judge;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public UUID create(UUID actor, Content content) {
        String role = requireRole(actor, "TEACHER", "ADMIN");
        validate(content);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tbl_public_problem(id,author_id,status) VALUES(:id,:actor,'DRAFT')",
                Map.of("id", id, "actor", actor));
        revision(id, content);
        if ("ADMIN".equals(role))
            jdbc.update(
                    "UPDATE tbl_public_problem SET review_status='APPROVED' WHERE id=:id",
                    Map.of("id", id));
        audit(actor, role, id, "PROBLEM_CREATE", null, "DRAFT", null);
        return id;
    }

    @Transactional
    public void edit(UUID actor, UUID id, Content content) {
        requireRole(actor, "TEACHER");
        var row = problem(id, true);
        owner(actor, row);
        if (!"DRAFT".equals(row.get("status"))
                || !Set.of("DRAFT", "REJECTED", "CHANGES_REQUESTED")
                        .contains(row.get("review_status")))
            throw failure(HttpStatus.CONFLICT, "PROBLEM_NOT_EDITABLE");
        validate(content);
        revision(id, content);
        jdbc.update(
                "UPDATE tbl_public_problem SET"
                    + " review_status='DRAFT',review_reason=NULL,updated_at=clock_timestamp() WHERE"
                    + " id=:id",
                Map.of("id", id));
        audit(actor, "TEACHER", id, "PROBLEM_EDIT", (String) row.get("status"), "DRAFT", null);
    }

    @Transactional
    public void submitReview(UUID actor, UUID id) {
        requireRole(actor, "TEACHER");
        var row = problem(id, true);
        owner(actor, row);
        if (!"DRAFT".equals(row.get("status")) || !"DRAFT".equals(row.get("review_status")))
            throw failure(HttpStatus.CONFLICT, "PROBLEM_NOT_DRAFT");
        jdbc.update(
                "UPDATE tbl_public_problem SET"
                    + " review_status='PENDING_REVIEW',updated_at=clock_timestamp() WHERE id=:id",
                Map.of("id", id));
        audit(actor, "TEACHER", id, "PROBLEM_SUBMIT_REVIEW", "DRAFT", "PENDING_REVIEW", null);
    }

    @Transactional
    public void review(UUID actor, UUID id, boolean approve, String reason) {
        review(
                actor,
                id,
                approve ? ProblemDtos.ReviewDecision.APPROVE : ProblemDtos.ReviewDecision.REJECT,
                reason,
                approve);
    }

    @Transactional
    public void review(
            UUID actor,
            UUID id,
            ProblemDtos.ReviewDecision decision,
            String reason,
            boolean publish) {
        requireRole(actor, "ADMIN");
        var row = problem(id, true);
        if (!"PENDING_REVIEW".equals(row.get("review_status"))
                || !"DRAFT".equals(row.get("status")))
            throw failure(HttpStatus.CONFLICT, "PROBLEM_NOT_PENDING");
        if (decision != ProblemDtos.ReviewDecision.APPROVE && (reason == null || reason.isBlank()))
            throw failure(HttpStatus.BAD_REQUEST, "REVIEW_REASON_REQUIRED");
        String outcome =
                switch (decision) {
                    case APPROVE -> "APPROVED";
                    case REJECT -> "REJECTED";
                    case CHANGES_REQUESTED -> "CHANGES_REQUESTED";
                };
        var params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("review", outcome);
        params.put("reason", reason);
        params.put(
                "status",
                publish && decision == ProblemDtos.ReviewDecision.APPROVE ? "PUBLISHED" : "DRAFT");
        jdbc.update(
                "UPDATE tbl_public_problem SET"
                    + " review_status=:review,status=:status,review_reason=:reason,updated_at=CURRENT_TIMESTAMP"
                    + " WHERE id=:id",
                params);
        audit(actor, "ADMIN", id, "PROBLEM_REVIEW", "PENDING_REVIEW", outcome, reason);
        if (decision != ProblemDtos.ReviewDecision.CHANGES_REQUESTED)
            notifier.notifyAuthor((UUID) row.get("author_id"), id, outcome, reason);
    }

    public PageResponse<Summary> browse(UUID actor, Pageable page) {
        requireRole(actor, "STUDENT", "TEACHER");
        int size = Math.min(100, page.getPageSize());
        long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tbl_public_problem WHERE status='PUBLISHED'",
                        Map.of(),
                        Long.class);
        var rows =
                jdbc.query(
                        "SELECT p.id,r.title,p.status,p.created_at FROM tbl_public_problem p JOIN"
                            + " tbl_problem_revision r ON r.id=p.current_revision_id WHERE"
                            + " p.status='PUBLISHED' ORDER BY p.created_at DESC,p.id LIMIT :limit"
                            + " OFFSET :offset",
                        Map.of("limit", size, "offset", (long) page.getPageNumber() * size),
                        (rs, n) ->
                                new Summary(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("title"),
                                        rs.getString("status"),
                                        rs.getTimestamp("created_at").toInstant()));
        return PageResponse.fromPage(
                new PageImpl<>(rows, PageRequest.of(page.getPageNumber(), size), count));
    }

    public View detail(UUID actor, UUID id) {
        requireRole(actor, "STUDENT", "TEACHER");
        var row = problem(id, false);
        String status = (String) row.get("status");
        if (!Set.of("PUBLISHED", "UNPUBLISHED").contains(status))
            throw failure(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND");
        return view(id, snapshot(row), status);
    }

    @Override
    public ContentSnapshot contentSnapshot(UUID teacher, UUID id) {
        requireRole(teacher, "TEACHER");
        var row = problem(id, false);
        published(row);
        return snapshot(row);
    }

    public Contribution contribution(UUID actor, UUID id, boolean reviewQueue) {
        requireRole(actor, reviewQueue ? "ADMIN" : "TEACHER");
        var row = problem(id, false);
        if (!reviewQueue) owner(actor, row);
        return new Contribution(
                id,
                (String) row.get("review_status"),
                (String) row.get("review_reason"),
                snapshot(row),
                (String) row.get("status"));
    }

    public PageResponse<Summary> contributions(UUID actor, boolean reviewQueue, Pageable page) {
        requireRole(actor, reviewQueue ? "ADMIN" : "TEACHER");
        int size = Math.min(100, page.getPageSize());
        String from =
                " FROM tbl_public_problem p JOIN tbl_problem_revision r ON"
                    + " r.id=p.current_revision_id WHERE "
                        + (reviewQueue ? "p.review_status='PENDING_REVIEW'" : "p.author_id=:actor");
        var params =
                Map.<String, Object>of(
                        "actor",
                        actor,
                        "limit",
                        size,
                        "offset",
                        (long) page.getPageNumber() * size);
        long count = jdbc.queryForObject("SELECT COUNT(*)" + from, params, Long.class);
        var items =
                jdbc.query(
                        "SELECT p.id,r.title,p.review_status AS status,p.created_at"
                                + from
                                + " ORDER BY p.created_at DESC,p.id LIMIT :limit OFFSET :offset",
                        params,
                        (rs, n) ->
                                new Summary(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("title"),
                                        rs.getString("status"),
                                        rs.getTimestamp("created_at").toInstant()));
        return PageResponse.fromPage(
                new PageImpl<>(items, PageRequest.of(page.getPageNumber(), size), count));
    }

    @Transactional
    public UUID evaluate(UUID actor, UUID id, CodeInput input, boolean run) {
        requireRole(actor, "STUDENT", "TEACHER");
        if (input.requestKey() == null || input.requestKey().length() > 64)
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_KEY");
        var row = problem(id, true);
        published(row);
        var content = snapshot(row);
        if (!content.languages().contains(input.language()))
            throw failure(HttpStatus.BAD_REQUEST, "LANGUAGE_NOT_ALLOWED");
        var r =
                new CodeEvaluationRequest(
                        actor,
                        null,
                        null,
                        input.source(),
                        input.language(),
                        content.testCases(),
                        "public:" + id + ":" + input.requestKey(),
                        0,
                        content.feedback(),
                        SubmissionScope.PUBLIC_PROBLEM,
                        id,
                        content.revisionId(),
                        content.limits());
        return run ? judge.run(r) : judge.submit(r);
    }

    public PageResponse<Submission> history(UUID actor, UUID id, Pageable page) {
        requireRole(actor, "STUDENT", "TEACHER");
        return submissionHistory(jdbc, actor, id, "PUBLIC_PROBLEM", page);
    }

    public record Judgement(UUID jobId, long generation, String status, Instant createdAt, Integer testsPassed, Integer totalTests, Long runtimeMs, Long memoryUsedKb) {}

    public record SubmissionDetail(
            UUID id,
            Instant submittedAt,
            String language,
            String source,
            UUID revisionId,
            List<Judgement> judgements) {}

    public SubmissionDetail submission(UUID actor, UUID problem, UUID submission) {
        requireRole(actor, "STUDENT", "TEACHER");
        var params = Map.of("actor", actor, "problem", problem, "submission", submission);
        var rows =
                jdbc.queryForList(
                        """
SELECT id,created_at,language,source,revision_id FROM tbl_submission
WHERE owner_id=:actor AND scope='PUBLIC_PROBLEM' AND resource_id=:problem AND id=:submission
""",
                        params);
        if (rows.isEmpty()) throw failure(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND");
        var history =
                jdbc.query(
                        "SELECT id,generation,status,created_at,(result::jsonb->>'passed')::integer,(result::jsonb->>'total')::integer,"
                            + "(result::jsonb->>'runtimeMs')::bigint,(result::jsonb->>'memoryUsedKb')::bigint FROM tbl_judge_job WHERE"
                            + " submission_id=:submission ORDER BY generation,created_at,id",
                        params,
                        (rs, n) ->
                                new Judgement(
                                        rs.getObject(1, UUID.class),
                                        rs.getLong(2),
                                        rs.getString(3),
                                        rs.getTimestamp(4).toInstant(),rs.getObject(5,Integer.class),rs.getObject(6,Integer.class),
                                        rs.getObject(7,Long.class),rs.getObject(8,Long.class)));
        var row = rows.getFirst();
        return new SubmissionDetail(
                submission,
                ((java.sql.Timestamp) row.get("created_at")).toInstant(),
                (String) row.get("language"),
                (String) row.get("source"),
                (UUID) row.get("revision_id"),
                history);
    }

    public EvaluationResult result(UUID actor, UUID id, UUID job) {
        requireRole(actor, "STUDENT", "TEACHER");
        requireJobScope(jdbc, actor, id, job, "PUBLIC_PROBLEM");
        return judge.result(actor, job);
    }

    public Statistics statistics(UUID actor) {
        requireRole(actor, "STUDENT", "TEACHER");
        var accepted =
                jdbc.query(
                        """
SELECT resource_id,created_at FROM vw_public_submission_outcome
WHERE owner_id=:owner AND status='ACCEPTED' ORDER BY created_at
""",
                        Map.of("owner", actor),
                        (rs, n) ->
                                Map.entry(
                                        rs.getObject(1, UUID.class),
                                        rs.getTimestamp(2).toInstant()));
        long solved = accepted.stream().map(Map.Entry::getKey).distinct().count();
        var days = new TreeSet<LocalDate>();
        accepted.forEach(
                e -> days.add(e.getValue().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()));
        int highest = 0, chain = 0;
        LocalDate previous = null;
        for (var day : days) {
            chain = previous != null && previous.plusDays(1).equals(day) ? chain + 1 : 1;
            highest = Math.max(highest, chain);
            previous = day;
        }
        LocalDate today = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        int current = previous != null && !previous.isBefore(today.minusDays(1)) ? chain : 0;
        var byDifficulty = new EnumMap<ProblemDtos.Difficulty, Long>(ProblemDtos.Difficulty.class);
        for (var level : ProblemDtos.Difficulty.values()) byDifficulty.put(level, 0L);
        jdbc.query(
                """
                SELECT r.difficulty,count(DISTINCT o.resource_id)
                FROM vw_public_submission_outcome o JOIN tbl_public_problem p ON p.id=o.resource_id
                JOIN tbl_problem_revision r ON r.id=p.current_revision_id
                WHERE o.owner_id=:actor AND o.status='ACCEPTED' GROUP BY r.difficulty
                """,
                Map.of("actor", actor),
                (org.springframework.jdbc.core.RowCallbackHandler)
                        rs ->
                                byDifficulty.put(
                                        ProblemDtos.Difficulty.valueOf(rs.getString(1)),
                                        rs.getLong(2)));
        var totals =
                jdbc.queryForMap(
                        "SELECT count(*) AS total,count(status) AS judged FROM"
                            + " vw_public_submission_outcome WHERE owner_id=:actor",
                        Map.of("actor", actor));
        long judged = ((Number) totals.get("judged")).longValue();
        return new Statistics(
                solved,
                current,
                highest,
                byDifficulty,
                ((Number) totals.get("total")).longValue(),
                judged == 0 ? 0 : 100.0 * accepted.size() / judged);
    }

    public static View view(UUID id, ContentSnapshot s, String status) {
        return new View(
                id,
                s.revisionId(),
                s.title(),
                s.statement(),
                s.languages(),
                ProblemPolicy.publicSamples(s.testCases()),
                s.limits(),
                status);
    }

    public static PageResponse<Submission> submissionHistory(
            NamedParameterJdbcTemplate jdbc,
            UUID owner,
            UUID resource,
            String scope,
            Pageable page) {
        var p = new HashMap<String, Object>();
        p.put("owner", owner);
        p.put("resource", resource);
        p.put("scope", scope);
        p.put("limit", Math.min(100, page.getPageSize()));
        p.put("offset", (long) page.getPageNumber() * (int) p.get("limit"));
        String from =
                """
 FROM tbl_submission s JOIN LATERAL (
    SELECT id,status FROM tbl_judge_job WHERE submission_id=s.id AND mode='SUBMIT'
    ORDER BY (status IN ('ACCEPTED','WRONG_ANSWER','COMPILE_ERROR','RUNTIME_ERROR','TIME_LIMIT_EXCEEDED','MEMORY_LIMIT_EXCEEDED','OUTPUT_LIMIT_EXCEEDED')) DESC,
             generation DESC,created_at DESC,id LIMIT 1
 ) j ON true WHERE s.owner_id=:owner AND s.resource_id=:resource AND s.scope=:scope
""";
        long count = jdbc.queryForObject("SELECT COUNT(*)" + from, p, Long.class);
        var rows =
                jdbc.query(
                        "SELECT j.id,s.id AS submission_id,j.status,s.created_at"
                                + from
                                + " ORDER BY s.created_at DESC,s.id LIMIT :limit OFFSET :offset",
                        p,
                        (rs, n) ->
                                new Submission(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("submission_id", UUID.class),
                                        rs.getString("status"),
                                        rs.getTimestamp("created_at").toInstant()));
        return PageResponse.fromPage(
                new PageImpl<>(
                        rows, PageRequest.of(page.getPageNumber(), (int) p.get("limit")), count));
    }

    public static void requireJobScope(
            NamedParameterJdbcTemplate jdbc, UUID owner, UUID resource, UUID job, String scope) {
        long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tbl_judge_job WHERE id=:job AND owner_id=:owner AND"
                            + " (payload::jsonb->>'resourceId')=:resource AND"
                            + " (payload::jsonb->>'scope')=:scope",
                        Map.of(
                                "job",
                                job,
                                "owner",
                                owner,
                                "resource",
                                resource.toString(),
                                "scope",
                                scope),
                        Long.class);
        if (count != 1) throw failure(HttpStatus.NOT_FOUND, "JUDGE_JOB_NOT_FOUND");
    }

    @Transactional
    public void adminEdit(UUID actor, UUID id, Content content) {
        requireRole(actor, "ADMIN");
        var row = problem(id, true);
        if ("ARCHIVED".equals(row.get("status"))
                || "PENDING_REVIEW".equals(row.get("review_status")))
            throw failure(HttpStatus.CONFLICT, "PROBLEM_NOT_EDITABLE");
        validate(content);
        revision(id, content);
        jdbc.update(
                "UPDATE tbl_public_problem SET review_status='APPROVED',review_reason=NULL WHERE"
                    + " id=:id",
                Map.of("id", id));
        audit(actor, "ADMIN", id, "PROBLEM_EDIT", null, null, null);
    }

    @Transactional
    public void publication(UUID actor, UUID id, ProblemDtos.Publication target) {
        requireRole(actor, "ADMIN");
        var row = problem(id, true);
        String before = (String) row.get("status");
        if ("ARCHIVED".equals(before)
                || (target == ProblemDtos.Publication.PUBLISHED
                        && !"APPROVED".equals(row.get("review_status")))
                || (target == ProblemDtos.Publication.UNPUBLISHED && !"PUBLISHED".equals(before)))
            throw failure(HttpStatus.CONFLICT, "PROBLEM_STATE_CONFLICT");
        jdbc.update(
                "UPDATE tbl_public_problem SET status=:status,updated_at=CURRENT_TIMESTAMP WHERE"
                    + " id=:id",
                Map.of("id", id, "status", target.name()));
        audit(actor, "ADMIN", id, "PROBLEM_PUBLICATION_CHANGED", before, target.name(), null);
    }

    @Transactional
    public void delete(UUID actor, UUID id) {
        requireRole(actor, "ADMIN");
        var row = problem(id, true);
        var params = Map.of("id", id);
        boolean dependent =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                """
SELECT EXISTS(SELECT FROM tbl_submission WHERE scope='PUBLIC_PROBLEM' AND resource_id=:id)
    OR EXISTS(SELECT FROM tbl_class_practice WHERE source_problem_id=:id)
    OR EXISTS(SELECT FROM tbl_saved_problem WHERE problem_id=:id)
    OR EXISTS(SELECT FROM tbl_problem_report WHERE problem_id=:id)
    OR EXISTS(SELECT FROM tbl_public_course_unit WHERE content_blocks @> jsonb_build_array(jsonb_build_object('problemId',CAST(:id AS text))))
    OR EXISTS(SELECT FROM tbl_judge_problem WHERE context_type='PUBLIC_PROBLEM' AND context_id=:id)
""",
                                params,
                                Boolean.class));
        if (dependent) throw failure(HttpStatus.CONFLICT, "PROBLEM_ARCHIVE_REQUIRED");
        jdbc.update("DELETE FROM tbl_public_problem WHERE id=:id", params);
        audit(actor, "ADMIN", id, "PROBLEM_DELETE", (String) row.get("status"), null, null);
    }

    private void revision(UUID id, Content c) {
        UUID revision = UUID.randomUUID();
        var snapshot =
                new ContentSnapshot(
                        revision,
                        c.title().trim(),
                        c.statement().trim(),
                        c.languages(),
                        c.testCases(),
                        c.limits(),
                        c.feedback(),
                        c.difficulty(),
                        c.tags(),
                        c.editorial());
        var params = new HashMap<String, Object>();
        params.put("revision", revision);
        params.put("problem", id);
        params.put("title", snapshot.title());
        params.put("payload", encode(snapshot));
        params.put(
                "difficulty",
                Objects.requireNonNullElse(c.difficulty(), ProblemDtos.Difficulty.EASY).name());
        params.put("tags", encode(c.tags() == null ? Set.of() : c.tags()));
        params.put("editorial", c.editorial());
        jdbc.update(
                "INSERT INTO"
                    + " tbl_problem_revision(id,problem_id,title,payload,difficulty,tags,editorial)"
                    + " VALUES(:revision,:problem,:title,:payload,:difficulty,CAST(:tags AS"
                    + " jsonb),:editorial)",
                params);
        jdbc.update(
                "UPDATE tbl_public_problem SET"
                    + " current_revision_id=:revision,updated_at=clock_timestamp() WHERE id=:id",
                Map.of("revision", revision, "id", id));
    }

    private Map<String, Object> problem(UUID id, boolean lock) {
        var rows =
                jdbc.queryForList(
                        "SELECT p.*,r.payload FROM tbl_public_problem p JOIN tbl_problem_revision r"
                            + " ON r.id=p.current_revision_id WHERE p.id=:id"
                                + (lock ? " FOR UPDATE OF p" : ""),
                        Map.of("id", id));
        if (rows.isEmpty()) throw failure(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND");
        return rows.getFirst();
    }

    private ContentSnapshot snapshot(Map<String, Object> row) {
        try {
            return json.readValue((String) row.get("payload"), ContentSnapshot.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Invalid problem snapshot", e);
        }
    }

    private String encode(Object c) {
        try {
            return json.writeValueAsString(c);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Problem snapshot serialization failed", e);
        }
    }

    private static void published(Map<String, Object> row) {
        if (!"PUBLISHED".equals(row.get("status")))
            throw failure(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND");
    }

    private static void owner(UUID actor, Map<String, Object> row) {
        if (!actor.equals(row.get("author_id")))
            throw failure(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION");
    }

    public String requireRole(UUID actor, String... roles) {
        var rows =
                jdbc.queryForList(
                        "SELECT role FROM tbl_account WHERE id=:id AND status='ACTIVE'",
                        Map.of("id", actor));
        if (rows.isEmpty() || !List.of(roles).contains(rows.getFirst().get("role")))
            throw failure(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION");
        return (String) rows.getFirst().get("role");
    }

    private void audit(
            UUID actor,
            String role,
            UUID id,
            String action,
            String before,
            String after,
            String reason) {
        audit.record(
                actor, role, "PUBLIC_PROBLEM", id.toString(), action, before, after, reason, null);
    }

    private static void validate(Content c) {
        if (c == null
                || c.title() == null
                || c.title().isBlank()
                || c.title().length() > 255
                || c.statement() == null
                || c.statement().isBlank()
                || c.statement().length() > 100000
                || c.languages() == null
                || c.languages().isEmpty()
                || c.languages().stream().anyMatch(java.util.Objects::isNull)
                || c.testCases() == null
                || c.testCases().isEmpty()
                || c.testCases().size() > 100
                || c.limits() == null
                || c.limits().compileSeconds() < 1
                || c.limits().compileSeconds() > 10
                || c.limits().runtimeMillis() < 1
                || c.limits().runtimeMillis() > 5000
                || c.limits().outputBytes() < 1
                || c.limits().outputBytes() > 65536
                || c.feedback() == null)
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_PROBLEM_CONTENT");
        long bytes = 0;
        for (var t : c.testCases()) {
            if (t == null || t.input() == null || t.expectedOutput() == null)
                throw failure(HttpStatus.BAD_REQUEST, "INVALID_TESTCASE");
            int a = t.input().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                    b = t.expectedOutput().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (a > 65536 || b > 65536) throw failure(HttpStatus.BAD_REQUEST, "INVALID_TESTCASE");
            bytes += a + b;
        }
        if (bytes > 1048576 || c.testCases().stream().noneMatch(TestCaseSnapshot::sample))
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_TESTCASE");
    }

    public static AppException failure(HttpStatus status, String code) {
        return new AppException("Problem request rejected", status, code) {};
    }
}
