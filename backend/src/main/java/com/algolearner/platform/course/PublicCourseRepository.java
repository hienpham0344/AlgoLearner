package com.algolearner.platform.course;

import com.algolearner.platform.course.CourseDtos.AnswerRequest;
import com.algolearner.platform.course.CourseDtos.AnswerVisibility;
import com.algolearner.platform.course.CourseDtos.AttemptStatus;
import com.algolearner.platform.course.CourseDtos.Block;
import com.algolearner.platform.course.CourseDtos.CompletionRule;
import com.algolearner.platform.course.CourseDtos.CourseContent;
import com.algolearner.platform.course.CourseDtos.LessonContent;
import com.algolearner.platform.course.CourseDtos.Level;
import com.algolearner.platform.course.CourseDtos.ProgressStatus;
import com.algolearner.platform.course.CourseDtos.Publication;
import com.algolearner.platform.course.CourseDtos.QuestionContent;
import com.algolearner.platform.course.CourseDtos.QuizContent;
import com.algolearner.platform.course.CourseDtos.ReviewStatus;
import com.algolearner.platform.course.CourseDtos.UnpublishPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mock")
@RequiredArgsConstructor
public class PublicCourseRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public record CourseRow(UUID id, UUID authorId, String authorName, String authorRole, String title, String description,
                            Level level, String topic, Integer estimatedMinutes, Publication status,
                            UnpublishPolicy unpublishPolicy, Instant publishedAt, Instant contentUpdatedAt,
                            Instant createdAt, Instant updatedAt, int lessonCount, int quizCount) {
        CourseDtos.Summary summary() {
            return new CourseDtos.Summary(id, authorId, authorName, title, description, level, topic, estimatedMinutes, status,
                    unpublishPolicy, lessonCount, quizCount, publishedAt, createdAt, updatedAt);
        }
    }
    public record LessonRow(UUID id, UUID courseId, String title, int displayOrder, Integer estimatedMinutes,
                            String prerequisiteNote, boolean preview, List<Block> blocks, Instant removedAt) {
        LessonContent content() { return new LessonContent(id, title, estimatedMinutes, prerequisiteNote, preview, blocks); }
    }
    public record QuizRow(UUID id, UUID courseId, UUID lessonId, String title, boolean mandatory, CompletionRule rule,
                          BigDecimal passingScore, Integer maxAttempts, Integer timeLimitSeconds,
                          AnswerVisibility visibility, List<QuestionContent> questions, int displayOrder, Instant removedAt) {
        QuizContent content() {
            return new QuizContent(id, lessonId, title, mandatory, rule, passingScore, maxAttempts, timeLimitSeconds, visibility, questions);
        }
    }
    public record EnrollmentRow(UUID id, UUID courseId, UUID accountId, ProgressStatus status, BigDecimal percent,
                                Instant enrolledAt, Instant startedAt, Instant completedAt, boolean hasUpdate,
                                Instant unenrolledAt) {
        boolean active() { return unenrolledAt == null; }
    }
    public record RevisionRow(UUID id, UUID courseId, UUID authorId, String authorName, int revisionNo, ReviewStatus status,
                              CourseContent snapshot, String reason, UUID reviewerId, Instant submittedAt,
                              Instant reviewedAt, Instant updatedAt) { }
    public record AttemptRow(UUID id, UUID quizId, UUID enrollmentId, int attemptNo, AttemptStatus status, Instant startedAt,
                             Instant deadlineAt, Instant submittedAt, BigDecimal score, Boolean passed,
                             QuizContent quiz, Map<UUID, Set<UUID>> answers) { }

    private static final String COURSE_SELECT = """
            SELECT c.*, a.username AS author_name, a.role AS author_role,
              (SELECT count(*) FROM tbl_public_course_unit u WHERE u.course_id=c.id AND u.removed_at IS NULL) AS lesson_count,
              (SELECT count(*) FROM tbl_public_course_quiz q WHERE q.course_id=c.id AND q.removed_at IS NULL) AS quiz_count
            FROM tbl_public_course c JOIN tbl_account a ON a.id=c.author_id
            """;

    private final RowMapper<CourseRow> courseMapper = (rs, n) -> new CourseRow(rs.getObject("id", UUID.class),
            rs.getObject("author_id", UUID.class), rs.getString("author_name"), rs.getString("author_role"),
            rs.getString("title"), rs.getString("description"), enumOrNull(Level.class, rs.getString("level")),
            rs.getString("topic"), (Integer) rs.getObject("estimated_minutes"), Publication.valueOf(rs.getString("status")),
            enumOrNull(UnpublishPolicy.class, rs.getString("unpublish_policy")), instant(rs, "published_at"),
            instant(rs, "content_updated_at"), instant(rs, "created_at"), instant(rs, "updated_at"),
            rs.getInt("lesson_count"), rs.getInt("quiz_count"));

    private final RowMapper<LessonRow> lessonMapper = (rs, n) -> new LessonRow(rs.getObject("id", UUID.class),
            rs.getObject("course_id", UUID.class), rs.getString("title"), rs.getInt("display_order"),
            (Integer) rs.getObject("estimated_minutes"), rs.getString("prerequisite_note"), rs.getBoolean("is_preview"),
            read(rs.getString("content_blocks"), new TypeReference<List<Block>>() { }), instant(rs, "removed_at"));

    private final RowMapper<QuizRow> quizMapper = (rs, n) -> new QuizRow(rs.getObject("id", UUID.class),
            rs.getObject("course_id", UUID.class), rs.getObject("lesson_id", UUID.class), rs.getString("title"),
            rs.getBoolean("mandatory"), CompletionRule.valueOf(rs.getString("completion_rule")), rs.getBigDecimal("passing_score"),
            (Integer) rs.getObject("max_attempts"), (Integer) rs.getObject("time_limit_seconds"),
            AnswerVisibility.valueOf(rs.getString("answer_visibility")),
            read(rs.getString("questions"), new TypeReference<List<QuestionContent>>() { }), rs.getInt("display_order"),
            instant(rs, "removed_at"));

    private final RowMapper<EnrollmentRow> enrollmentMapper = (rs, n) -> new EnrollmentRow(rs.getObject("id", UUID.class),
            rs.getObject("course_id", UUID.class), rs.getObject("account_id", UUID.class),
            ProgressStatus.valueOf(rs.getString("progress_status")), rs.getBigDecimal("progress_percent"),
            instant(rs, "created_at"), instant(rs, "started_at"), instant(rs, "completed_at"), rs.getBoolean("has_update"),
            instant(rs, "unenrolled_at"));

    private final RowMapper<RevisionRow> revisionMapper = (rs, n) -> new RevisionRow(rs.getObject("id", UUID.class),
            rs.getObject("course_id", UUID.class), rs.getObject("author_id", UUID.class), rs.getString("author_name"),
            rs.getInt("revision_no"), ReviewStatus.valueOf(rs.getString("status")),
            read(rs.getString("snapshot"), new TypeReference<CourseContent>() { }), rs.getString("reason"),
            rs.getObject("reviewer_id", UUID.class), instant(rs, "submitted_at"), instant(rs, "reviewed_at"),
            instant(rs, "updated_at"));

    private final RowMapper<AttemptRow> attemptMapper = (rs, n) -> new AttemptRow(rs.getObject("id", UUID.class),
            rs.getObject("quiz_id", UUID.class), rs.getObject("enrollment_id", UUID.class), rs.getInt("attempt_no"),
            AttemptStatus.valueOf(rs.getString("status")), instant(rs, "started_at"), instant(rs, "deadline_at"),
            instant(rs, "submitted_at"), rs.getBigDecimal("score"), (Boolean) rs.getObject("passed"),
            read(rs.getString("quiz_snapshot"), new TypeReference<QuizContent>() { }),
            answerMap(read(rs.getString("answers"), new TypeReference<List<AnswerRequest>>() { })));

    // ---------------------------------------------------------------- courses
    public Optional<CourseRow> course(UUID id, boolean lock) {
        if (lock) jdbc.query("SELECT id FROM tbl_public_course WHERE id=? FOR UPDATE", (rs, n) -> 1, id);
        return jdbc.query(COURSE_SELECT + " WHERE c.id=?", courseMapper, id).stream().findFirst();
    }

    public void insertCourse(UUID id, UUID author, CourseContent metadata, Instant now) {
        jdbc.update("""
                INSERT INTO tbl_public_course(id,author_id,title,description,level,topic,estimated_minutes,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,'DRAFT',?,?)
                """, id, author, metadata.title(), metadata.description(), name(metadata.level()), metadata.topic(),
                metadata.estimatedMinutes(), ts(now), ts(now));
    }

    public void updateMetadata(UUID id, CourseContent content, Instant now) {
        jdbc.update("""
                UPDATE tbl_public_course SET title=?,description=?,level=?,topic=?,estimated_minutes=?,content_updated_at=?,updated_at=?
                WHERE id=?
                """, content.title(), content.description(), name(content.level()), content.topic(), content.estimatedMinutes(),
                ts(now), ts(now), id);
    }

    public void setPublication(UUID id, Publication status, UnpublishPolicy policy, Instant now) {
        jdbc.update("""
                UPDATE tbl_public_course SET status=?,unpublish_policy=?,
                  published_at=CASE WHEN ?='PUBLISHED' THEN COALESCE(published_at,?) ELSE published_at END,updated_at=?
                WHERE id=?
                """, status.name(), name(policy), status.name(), ts(now), ts(now), id);
    }

    public record CatalogFilter(String query, Level level, String topic, Collection<Publication> statuses, UUID author) { }

    public List<CourseRow> courses(CatalogFilter filter, int page, int size) {
        var args = new ArrayList<Object>();
        String where = where(filter, args);
        args.add(size);
        args.add((long) page * size);
        return jdbc.query(COURSE_SELECT + where + " ORDER BY COALESCE(c.published_at,c.created_at) DESC,c.id LIMIT ? OFFSET ?",
                courseMapper, args.toArray());
    }

    public long countCourses(CatalogFilter filter) {
        var args = new ArrayList<Object>();
        return jdbc.queryForObject("SELECT count(*) FROM tbl_public_course c" + where(filter, args), Long.class, args.toArray());
    }

    private String where(CatalogFilter filter, List<Object> args) {
        var sql = new StringBuilder(" WHERE c.status IN (");
        var statuses = new ArrayList<>(filter.statuses());
        for (int i = 0; i < statuses.size(); i++) { sql.append(i == 0 ? "?" : ",?"); args.add(statuses.get(i).name()); }
        sql.append(")");
        if (filter.query() != null) { sql.append(" AND c.title ILIKE ? ESCAPE '\\'"); args.add("%" + escapeLike(filter.query()) + "%"); }
        if (filter.level() != null) { sql.append(" AND c.level=?"); args.add(filter.level().name()); }
        if (filter.topic() != null) { sql.append(" AND lower(c.topic)=lower(?)"); args.add(filter.topic()); }
        if (filter.author() != null) { sql.append(" AND c.author_id=?"); args.add(filter.author()); }
        return sql.toString();
    }

    public void deleteCourse(UUID course) {
        jdbc.update("DELETE FROM tbl_public_course_revision WHERE course_id=?", course);
        jdbc.update("DELETE FROM tbl_public_course_quiz WHERE course_id=?", course);
        jdbc.update("DELETE FROM tbl_public_course_unit WHERE course_id=?", course);
        jdbc.update("DELETE FROM tbl_public_course WHERE id=?", course);
    }

    public long enrollmentCount(UUID course) {
        return jdbc.queryForObject("SELECT count(*) FROM tbl_course_enrollment WHERE course_id=?", Long.class, course);
    }

    public boolean publishedProblem(UUID problem) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM tbl_public_problem WHERE id=? AND status='PUBLISHED')", Boolean.class, problem));
    }

    // ---------------------------------------------------------------- lessons & quizzes
    public List<LessonRow> lessons(UUID course) {
        return jdbc.query("SELECT * FROM tbl_public_course_unit WHERE course_id=? AND removed_at IS NULL ORDER BY display_order,id",
                lessonMapper, course);
    }

    public Optional<LessonRow> lesson(UUID course, UUID lesson) {
        return jdbc.query("SELECT * FROM tbl_public_course_unit WHERE id=? AND course_id=? AND removed_at IS NULL",
                lessonMapper, lesson, course).stream().findFirst();
    }

    public List<QuizRow> quizzes(UUID course) {
        return jdbc.query("SELECT * FROM tbl_public_course_quiz WHERE course_id=? AND removed_at IS NULL ORDER BY display_order,id",
                quizMapper, course);
    }

    public Optional<QuizRow> quiz(UUID course, UUID quiz) {
        return jdbc.query("SELECT * FROM tbl_public_course_quiz WHERE id=? AND course_id=? AND removed_at IS NULL",
                quizMapper, quiz, course).stream().findFirst();
    }

    /** Applies approved/admin content: upsert by id, soft-remove what is gone. Ids owned by another course are rejected. */
    public void applyContent(UUID course, CourseContent content, Instant now) {
        Set<UUID> lessonIds = new HashSet<>();
        int order = 0;
        for (LessonContent lesson : content.lessons()) {
            lessonIds.add(lesson.id());
            int changed = jdbc.update("""
                    INSERT INTO tbl_public_course_unit(id,course_id,title,content,content_blocks,is_preview,estimated_minutes,
                        prerequisite_note,display_order,removed_at,created_at,updated_at)
                    VALUES (?,?,?,NULL,?::jsonb,?,?,?,?,NULL,?,?)
                    ON CONFLICT (id) DO UPDATE SET title=EXCLUDED.title,content_blocks=EXCLUDED.content_blocks,
                        is_preview=EXCLUDED.is_preview,estimated_minutes=EXCLUDED.estimated_minutes,
                        prerequisite_note=EXCLUDED.prerequisite_note,display_order=EXCLUDED.display_order,removed_at=NULL,
                        updated_at=EXCLUDED.updated_at
                    WHERE tbl_public_course_unit.course_id=EXCLUDED.course_id
                    """, lesson.id(), course, lesson.title(), write(lesson.blocks()), lesson.preview(), lesson.estimatedMinutes(),
                    lesson.prerequisiteNote(), order++, ts(now), ts(now));
            if (changed == 0) throw CourseException.invalid("Lesson id belongs to another course");
        }
        softRemove("tbl_public_course_unit", course, lessonIds, now);
        Set<UUID> quizIds = new HashSet<>();
        order = 0;
        for (QuizContent quiz : content.quizzes()) {
            quizIds.add(quiz.id());
            int changed = jdbc.update("""
                    INSERT INTO tbl_public_course_quiz(id,course_id,lesson_id,title,mandatory,completion_rule,passing_score,
                        max_attempts,time_limit_seconds,answer_visibility,questions,display_order,removed_at,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,NULL,?,?)
                    ON CONFLICT (id) DO UPDATE SET lesson_id=EXCLUDED.lesson_id,title=EXCLUDED.title,mandatory=EXCLUDED.mandatory,
                        completion_rule=EXCLUDED.completion_rule,passing_score=EXCLUDED.passing_score,
                        max_attempts=EXCLUDED.max_attempts,time_limit_seconds=EXCLUDED.time_limit_seconds,
                        answer_visibility=EXCLUDED.answer_visibility,questions=EXCLUDED.questions,
                        display_order=EXCLUDED.display_order,removed_at=NULL,updated_at=EXCLUDED.updated_at
                    WHERE tbl_public_course_quiz.course_id=EXCLUDED.course_id
                    """, quiz.id(), course, quiz.lessonId(), quiz.title(), quiz.mandatory(), quiz.completionRule().name(),
                    quiz.passingScore(), quiz.maxAttempts(), quiz.timeLimitSeconds(), quiz.answerVisibility().name(),
                    write(quiz.questions()), order++, ts(now), ts(now));
            if (changed == 0) throw CourseException.invalid("Quiz id belongs to another course");
        }
        softRemove("tbl_public_course_quiz", course, quizIds, now);
        updateMetadata(course, content, now);
    }

    private void softRemove(String table, UUID course, Set<UUID> keep, Instant now) {
        var args = new ArrayList<Object>(List.of(ts(now), ts(now), course));
        var sql = new StringBuilder("UPDATE " + table + " SET removed_at=?,updated_at=? WHERE course_id=? AND removed_at IS NULL");
        if (!keep.isEmpty()) {
            sql.append(" AND id NOT IN (").append(String.join(",", keep.stream().map(id -> "?").toList())).append(")");
            args.addAll(keep);
        }
        jdbc.update(sql.toString(), args.toArray());
    }

    public CourseContent liveContent(CourseRow course) {
        return new CourseContent(course.title(), course.description(), course.level(), course.topic(), course.estimatedMinutes(),
                lessons(course.id()).stream().map(LessonRow::content).toList(),
                quizzes(course.id()).stream().map(QuizRow::content).toList());
    }

    // ---------------------------------------------------------------- enrollments & progress
    public Optional<EnrollmentRow> enrollment(UUID account, UUID course, boolean lock) {
        return jdbc.query("SELECT * FROM tbl_course_enrollment WHERE account_id=? AND course_id=?" + (lock ? " FOR UPDATE" : ""),
                enrollmentMapper, account, course).stream().findFirst();
    }

    public List<EnrollmentRow> activeEnrollmentsForUpdate(UUID course) {
        return jdbc.query("SELECT * FROM tbl_course_enrollment WHERE course_id=? AND unenrolled_at IS NULL ORDER BY id FOR UPDATE",
                enrollmentMapper, course);
    }

    public UUID insertEnrollment(UUID account, UUID course, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_course_enrollment(id,course_id,account_id,progress_status,progress_percent,created_at,updated_at)
                VALUES (?,?,?,'NOT_STARTED',0,?,?)
                """, id, course, account, ts(now), ts(now));
        return id;
    }

    public void setUnenrolled(UUID enrollment, Instant unenrolledAt, Instant now) {
        jdbc.update("UPDATE tbl_course_enrollment SET unenrolled_at=?,updated_at=? WHERE id=?", ts(unenrolledAt), ts(now), enrollment);
    }

    public void updateProgress(UUID enrollment, ProgressStatus status, BigDecimal percent, Instant startedAt,
                               Instant completedAt, boolean hasUpdate, Instant now) {
        jdbc.update("""
                UPDATE tbl_course_enrollment SET progress_status=?,progress_percent=?,started_at=?,completed_at=?,has_update=?,
                    last_activity_at=?,updated_at=? WHERE id=?
                """, status.name(), percent, ts(startedAt), ts(completedAt), hasUpdate, ts(now), ts(now), enrollment);
    }

    public List<EnrollmentRow> enrollmentsOf(UUID account, int page, int size) {
        return jdbc.query("SELECT * FROM tbl_course_enrollment WHERE account_id=? AND unenrolled_at IS NULL ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
                enrollmentMapper, account, size, (long) page * size);
    }

    public long countEnrollmentsOf(UUID account) {
        return jdbc.queryForObject("SELECT count(*) FROM tbl_course_enrollment WHERE account_id=? AND unenrolled_at IS NULL", Long.class, account);
    }

    public Map<UUID, ProgressStatus> enrollmentStatuses(UUID account, Collection<UUID> courses) {
        var result = new LinkedHashMap<UUID, ProgressStatus>();
        if (courses.isEmpty()) return result;
        var args = new ArrayList<Object>(List.of(account));
        args.addAll(courses);
        jdbc.query("SELECT course_id,progress_status FROM tbl_course_enrollment WHERE account_id=? AND unenrolled_at IS NULL AND course_id IN ("
                        + String.join(",", courses.stream().map(c -> "?").toList()) + ")",
                (ResultSet rs) -> { result.put(rs.getObject(1, UUID.class), ProgressStatus.valueOf(rs.getString(2))); }, args.toArray());
        return result;
    }

    public boolean completeLesson(UUID enrollment, UUID lesson, Instant now) {
        return jdbc.update("""
                INSERT INTO tbl_course_unit_completion(id,enrollment_id,unit_id,created_at,updated_at) VALUES (?,?,?,?,?)
                ON CONFLICT(enrollment_id,unit_id) DO NOTHING
                """, UUID.randomUUID(), enrollment, lesson, ts(now), ts(now)) == 1;
    }

    public Set<UUID> completedLessonIds(UUID enrollment) {
        return new HashSet<>(jdbc.query("SELECT unit_id FROM tbl_course_unit_completion WHERE enrollment_id=?",
                (rs, n) -> rs.getObject(1, UUID.class), enrollment));
    }

    public Set<UUID> completedQuizIds(UUID enrollment) {
        return new HashSet<>(jdbc.query("SELECT DISTINCT quiz_id FROM tbl_public_course_quiz_attempt WHERE enrollment_id=? AND status='SUBMITTED' AND passed",
                (rs, n) -> rs.getObject(1, UUID.class), enrollment));
    }

    public boolean hasLearningActivity(UUID enrollment) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM tbl_course_unit_completion WHERE enrollment_id=?)
                    OR EXISTS(SELECT 1 FROM tbl_public_course_quiz_attempt WHERE enrollment_id=?)
                """, Boolean.class, enrollment, enrollment));
    }

    public void event(UUID enrollment, String event, String detail, Instant now) {
        jdbc.update("INSERT INTO tbl_course_enrollment_event(enrollment_id,event,detail,created_at,updated_at) VALUES (?,?,?,?,?)",
                enrollment, event, detail, ts(now), ts(now));
    }

    // ---------------------------------------------------------------- revisions
    private static final String REVISION_SELECT =
            "SELECT r.*, a.username AS author_name FROM tbl_public_course_revision r JOIN tbl_account a ON a.id=r.author_id";

    public Optional<RevisionRow> openRevision(UUID course) {
        return jdbc.query(REVISION_SELECT + " WHERE r.course_id=? AND r.status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED')",
                revisionMapper, course).stream().findFirst();
    }

    public Optional<RevisionRow> latestRevision(UUID course) {
        return jdbc.query(REVISION_SELECT + " WHERE r.course_id=? ORDER BY r.revision_no DESC LIMIT 1", revisionMapper, course)
                .stream().findFirst();
    }

    public Optional<RevisionRow> revision(UUID id) {
        return jdbc.query(REVISION_SELECT + " WHERE r.id=?", revisionMapper, id).stream().findFirst();
    }

    public RevisionRow insertRevision(UUID course, UUID author, CourseContent snapshot, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_public_course_revision(id,course_id,author_id,revision_no,status,snapshot,created_at,updated_at)
                VALUES (?,?,?,(SELECT COALESCE(max(revision_no),0)+1 FROM tbl_public_course_revision WHERE course_id=?),'DRAFT',?::jsonb,?,?)
                """, id, course, author, course, write(snapshot), ts(now), ts(now));
        return revision(id).orElseThrow();
    }

    public void updateRevisionSnapshot(UUID revision, CourseContent snapshot, ReviewStatus status, Instant now) {
        jdbc.update("UPDATE tbl_public_course_revision SET snapshot=?::jsonb,status=?,updated_at=? WHERE id=?",
                write(snapshot), status.name(), ts(now), revision);
    }

    public void submitRevision(UUID revision, Instant now) {
        jdbc.update("UPDATE tbl_public_course_revision SET status='PENDING_REVIEW',submitted_at=?,reviewed_at=NULL,updated_at=? WHERE id=?",
                ts(now), ts(now), revision);
    }

    public void reviewRevision(UUID revision, ReviewStatus status, String reason, UUID reviewer, Instant now) {
        jdbc.update("UPDATE tbl_public_course_revision SET status=?,reason=?,reviewer_id=?,reviewed_at=?,updated_at=? WHERE id=?",
                status.name(), reason, reviewer, ts(now), ts(now), revision);
    }

    public List<RevisionRow> revisions(ReviewStatus status, UUID author, int page, int size) {
        var args = new ArrayList<Object>(List.of(status.name()));
        String scope = author == null ? "" : " AND r.author_id=?";
        if (author != null) args.add(author);
        args.add(size);
        args.add((long) page * size);
        return jdbc.query(REVISION_SELECT + " WHERE r.status=?" + scope + " ORDER BY COALESCE(r.submitted_at,r.updated_at),r.id LIMIT ? OFFSET ?",
                revisionMapper, args.toArray());
    }

    public long countRevisions(ReviewStatus status, UUID author) {
        return author == null
                ? jdbc.queryForObject("SELECT count(*) FROM tbl_public_course_revision WHERE status=?", Long.class, status.name())
                : jdbc.queryForObject("SELECT count(*) FROM tbl_public_course_revision WHERE status=? AND author_id=?", Long.class,
                        status.name(), author);
    }

    // ---------------------------------------------------------------- attempts
    public List<AttemptRow> attempts(UUID quiz, UUID enrollment) {
        return jdbc.query("SELECT * FROM tbl_public_course_quiz_attempt WHERE quiz_id=? AND enrollment_id=? ORDER BY attempt_no",
                attemptMapper, quiz, enrollment);
    }

    public Optional<AttemptRow> attempt(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM tbl_public_course_quiz_attempt WHERE id=?" + (lock ? " FOR UPDATE" : ""), attemptMapper, id)
                .stream().findFirst();
    }

    public UUID insertAttempt(UUID quiz, UUID enrollment, int attemptNo, QuizContent snapshot, Instant started,
                              Instant deadline) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_public_course_quiz_attempt(id,quiz_id,enrollment_id,attempt_no,status,started_at,deadline_at,
                    quiz_snapshot,answers,created_at,updated_at) VALUES (?,?,?,?,'IN_PROGRESS',?,?,?::jsonb,'[]'::jsonb,?,?)
                """, id, quiz, enrollment, attemptNo, ts(started), ts(deadline), write(snapshot), ts(started), ts(started));
        return id;
    }

    public void saveAnswers(UUID attempt, Map<UUID, Set<UUID>> answers, Instant now) {
        jdbc.update("UPDATE tbl_public_course_quiz_attempt SET answers=?::jsonb,updated_at=? WHERE id=? AND status='IN_PROGRESS'",
                write(answerList(answers)), ts(now), attempt);
    }

    public void submitAttempt(UUID attempt, Instant submittedAt, BigDecimal score, boolean passed, Instant now) {
        jdbc.update("""
                UPDATE tbl_public_course_quiz_attempt SET status='SUBMITTED',submitted_at=?,score=?,passed=?,updated_at=?
                WHERE id=? AND status='IN_PROGRESS'
                """, ts(submittedAt), score, passed, ts(now), attempt);
    }

    // ---------------------------------------------------------------- helpers
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Course content serialization failed", error); }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Stored course content is unreadable", error); }
    }

    static Map<UUID, Set<UUID>> answerMap(List<AnswerRequest> answers) {
        var map = new LinkedHashMap<UUID, Set<UUID>>();
        for (AnswerRequest answer : answers) map.put(answer.questionId(), new HashSet<>(answer.optionIds()));
        return map;
    }

    private static List<AnswerRequest> answerList(Map<UUID, Set<UUID>> answers) {
        return answers.entrySet().stream()
                .map(e -> new AnswerRequest(e.getKey(), e.getValue().stream().sorted().toList())).toList();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String name(Enum<?> value) { return value == null ? null : value.name(); }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) { return value == null ? null : Enum.valueOf(type, value); }
}
