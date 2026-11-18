package com.algolearner.platform.profile.dashboard;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import static com.algolearner.platform.profile.dashboard.DashboardResponse.*;

/** Role-scoped summaries from canonical persisted learning records. */
@Repository
@Profile("internal-auth")
@Transactional(readOnly = true)
public class JdbcDashboardRepository implements DashboardRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcDashboardRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Preview<Classroom> studentClassrooms(Context c) {
        return preview("SELECT c.id, c.name, c.created_at FROM tbl_classroom c JOIN tbl_classroom_membership m ON m.classroom_id=c.id WHERE m.student_id=:account AND m.status='ACTIVE' AND c.status='ACTIVE'", c,
                (rs, row) -> new Classroom(rs.getObject("id", UUID.class), rs.getString("name")));
    }
    @Override public Preview<Classroom> teacherClassrooms(Context c) {
        return preview("SELECT c.id, c.name, c.created_at FROM tbl_classroom c WHERE c.owner_id=:account AND c.status='ACTIVE'", c,
                (rs, row) -> new Classroom(rs.getObject("id", UUID.class), rs.getString("name")));
    }
    @Override public Preview<ClassStatistics> classStatistics(Context c) {
        return preview("SELECT c.id, c.name, c.created_at, (SELECT COUNT(*) FROM tbl_classroom_membership m WHERE m.classroom_id=c.id AND m.status='ACTIVE') AS students, (SELECT COUNT(*) FROM tbl_assignment a WHERE a.classroom_id=c.id AND a.status='PUBLISHED') AS assignments, (SELECT COUNT(*) FROM tbl_quiz q WHERE q.classroom_id=c.id AND q.status='PUBLISHED') AS quizzes, (SELECT COUNT(*) FROM tbl_assignment_attempt t JOIN tbl_assignment a ON a.id=t.assignment_id LEFT JOIN tbl_assignment_evaluation e ON e.id=t.current_evaluation_id WHERE a.classroom_id=c.id AND t.submitted_at IS NOT NULL AND t.status='SUBMITTED' AND COALESCE(e.complete,false)=false) AS ungraded FROM tbl_classroom c WHERE c.owner_id=:account AND c.status='ACTIVE'", c,
                (rs, row) -> new ClassStatistics(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("students"), rs.getLong("assignments"), rs.getLong("quizzes"), rs.getLong("ungraded")));
    }
    @Override public Totals totals() {
        return new Totals(count("SELECT COUNT(*) FROM tbl_account WHERE role='STUDENT'"),
                count("SELECT COUNT(*) FROM tbl_account WHERE role='TEACHER'"), count("SELECT COUNT(*) FROM tbl_classroom"),
                count("SELECT COUNT(*) FROM tbl_public_course WHERE status='PUBLISHED'"),
                count("SELECT COUNT(*) FROM tbl_public_problem WHERE status='PUBLISHED'"),
                count("SELECT COUNT(*) FROM ("+OFFICIAL_EVENTS+") official"));
    }
    @Override public Moderation moderation(Context c) {
        long locked = jdbc.queryForObject("SELECT COUNT(*) FROM tbl_account WHERE login_locked_until>:now", Map.of("now", Timestamp.from(c.now())), Long.class);
        return new Moderation(locked, count("SELECT COUNT(*) FROM tbl_account WHERE status='SUSPENDED'"),
                count("SELECT COUNT(DISTINCT target_account_id) FROM tbl_account_violation WHERE revoked_at IS NULL"));
    }
    @Override public List<ChartEvent> chartEvents(Instant from, Instant to) {
        return jdbc.query("SELECT created_at, role AS metric FROM tbl_account WHERE role IN ('STUDENT','TEACHER') AND created_at>=:from AND created_at<:to UNION ALL SELECT created_at, 'CLASSROOM' AS metric FROM tbl_classroom WHERE created_at>=:from AND created_at<:to UNION ALL SELECT created_at, 'COURSE' AS metric FROM tbl_public_course WHERE status='PUBLISHED' AND created_at>=:from AND created_at<:to UNION ALL SELECT created_at, 'PROBLEM' AS metric FROM tbl_public_problem WHERE status='PUBLISHED' AND created_at>=:from AND created_at<:to UNION ALL SELECT created_at, 'SUBMISSION' AS metric FROM ("+OFFICIAL_EVENTS+") official WHERE created_at>=:from AND created_at<:to",
                Map.of("from", Timestamp.from(from), "to", Timestamp.from(to)),
                (rs, row) -> new ChartEvent(rs.getTimestamp("created_at").toInstant(), rs.getString("metric"), 1));
    }
    private <T> Preview<T> preview(String scopedQuery, Context c, RowMapper<T> mapper) {
        var params = Map.of("account", c.accountId(), "limit", Math.min(c.limit(),100), "now",Timestamp.from(c.now()),"end",Timestamp.from(c.upcomingEnd()));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + scopedQuery + ") scoped", params, Long.class);
        var items = jdbc.query("SELECT * FROM ("+scopedQuery+") scoped ORDER BY created_at DESC, id LIMIT :limit", params, mapper);
        return new Preview<>(items, total, total > items.size());
    }
    private long count(String sql) { return jdbc.queryForObject(sql, Map.of(), Long.class); }
    private static final String OFFICIAL_EVENTS = """
            SELECT id,submitted_at AS created_at FROM tbl_assignment_attempt WHERE submitted_at IS NOT NULL
            UNION ALL SELECT id,submitted_at AS created_at FROM tbl_quiz_attempt WHERE submitted_at IS NOT NULL
            UNION ALL SELECT id,created_at FROM tbl_submission WHERE scope IN ('PUBLIC_PROBLEM','CLASS_PRACTICE')
            """;
    private static final String OWNED_ATTEMPTS = """
            SELECT t.id,a.classroom_id,a.id AS assignment_id,NULL::uuid AS practice_id,
                   t.student_id,u.username,t.submitted_at AS created_at,'ASSIGNMENT' AS kind
            FROM tbl_assignment_attempt t JOIN tbl_assignment a ON a.id=t.assignment_id
            JOIN tbl_classroom c ON c.id=a.classroom_id JOIN tbl_account u ON u.id=t.student_id
            WHERE c.owner_id=:account AND c.status='ACTIVE' AND t.submitted_at IS NOT NULL
            """;
    private static final String UNGRADED = OWNED_ATTEMPTS + """
            AND t.status='SUBMITTED' AND NOT EXISTS
                (SELECT 1 FROM tbl_assignment_evaluation e WHERE e.id=t.current_evaluation_id AND e.complete)
            """;
    private static final String AUDIT_EVENTS = """
            SELECT 'audit-'||id AS id,action,outcome,created_at,target_type AS module FROM tbl_audit_log
            UNION ALL SELECT 'account-'||id AS id,action,'SUCCESS' AS outcome,created_at,'ACCOUNT' AS module
            FROM tbl_account_audit_log
            """;

    @Override public Preview<Assignment> studentAssignments(Context c) { return assignments(c, false); }
    @Override public Preview<Assignment> teacherAssignments(Context c) { return assignments(c, true); }
    private Preview<Assignment> assignments(Context c, boolean teacher) {
        String scope = teacher ? "c.owner_id=:account" : "EXISTS (SELECT 1 FROM tbl_classroom_membership m WHERE m.classroom_id=c.id AND m.student_id=:account AND m.status='ACTIVE')";
        String deadline = teacher ? "a.deadline" : "CASE WHEN a.start_time>:now THEN NULL::timestamptz ELSE a.deadline END";
        String open = teacher ? "(a.deadline IS NULL OR a.deadline>=:now OR a.allow_late_submission)"
                : "(a.deadline IS NULL OR a.deadline>=:now)";
        String upcoming = teacher ? "" : " AND ((a.start_time>:now AND a.start_time<:end) OR (a.deadline>=:now AND a.deadline<:end))";
        return preview("""
                SELECT a.id,a.classroom_id,c.name AS classroom_name,a.title,a.start_time,%s AS deadline,a.created_at
                FROM tbl_assignment a JOIN tbl_classroom c ON c.id=a.classroom_id
                WHERE c.status='ACTIVE' AND a.status IN ('PUBLISHED','SCHEDULED')
                AND (a.start_time IS NULL OR a.start_time<:end)
                AND %s AND
                """.formatted(deadline,open) + scope + upcoming, c, (rs,row) -> new Assignment(rs.getObject("id",UUID.class),rs.getObject("classroom_id",UUID.class),
                        rs.getString("classroom_name"),rs.getString("title"),instant(rs,"start_time"),instant(rs,"deadline")));
    }
    @Override public Preview<Quiz> studentQuizzes(Context c) {
        return preview("""
                SELECT q.id,q.classroom_id,c.name AS classroom_name,q.title,q.opens_at,q.closes_at,q.created_at
                FROM tbl_quiz q JOIN tbl_classroom c ON c.id=q.classroom_id
                WHERE c.status='ACTIVE' AND q.status='PUBLISHED'
                AND (q.opens_at IS NULL OR q.opens_at<:end) AND q.closes_at>=:now AND q.closes_at<:end
                AND EXISTS (SELECT 1 FROM tbl_classroom_membership m WHERE m.classroom_id=c.id
                            AND m.student_id=:account AND m.status='ACTIVE')
                """, c, (rs,row) -> new Quiz(rs.getObject("id",UUID.class),rs.getObject("classroom_id",UUID.class),
                        rs.getString("classroom_name"),rs.getString("title"),instant(rs,"opens_at"),instant(rs,"closes_at")));
    }
    @Override public Preview<Course> courses(Context c) {
        return preview("""
                SELECT p.id,p.title,e.created_at,
                    (SELECT COUNT(*) FROM tbl_public_course_unit u WHERE u.course_id=p.id) AS units,
                    (SELECT COUNT(*) FROM tbl_course_unit_completion done JOIN tbl_public_course_unit u ON u.id=done.unit_id
                     WHERE done.enrollment_id=e.id AND u.course_id=p.id) AS completed
                FROM tbl_course_enrollment e JOIN tbl_public_course p ON p.id=e.course_id
                WHERE e.account_id=:account AND p.status='PUBLISHED'
                """, c, (rs,row) -> {
                    int total=rs.getInt("units"), complete=rs.getInt("completed");
                    return new Course(rs.getObject("id",UUID.class),rs.getString("title"),complete,total,total==0 ? 0 : 100.0*complete/total);
                });
    }
    @Override public Preview<Submission> ungraded(Context c) { return preview(UNGRADED,c,this::submission); }
    @Override public Preview<Submission> recentSubmissions(Context c) {
        return preview(OWNED_ATTEMPTS + """
                UNION ALL
                SELECT s.id,p.classroom_id,NULL::uuid AS assignment_id,p.id AS practice_id,
                       s.owner_id AS student_id,u.username,s.created_at,'CLASS_PRACTICE' AS kind
                FROM tbl_submission s JOIN tbl_class_practice p ON p.id=s.resource_id
                JOIN tbl_classroom c ON c.id=p.classroom_id JOIN tbl_account u ON u.id=s.owner_id
                WHERE s.scope='CLASS_PRACTICE' AND c.owner_id=:account AND c.status='ACTIVE'
                """,c,this::submission);
    }
    private Submission submission(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new Submission(rs.getObject("id",UUID.class),rs.getString("kind"),rs.getObject("classroom_id",UUID.class),
                rs.getObject("assignment_id",UUID.class),rs.getObject("practice_id",UUID.class),
                rs.getObject("student_id",UUID.class),rs.getString("username"),instant(rs,"created_at"));
    }
    @Override public Contributions contributions(Context c, boolean allTeachers) {
        String scope=allTeachers ? "author.role='TEACHER'" : "p.author_id=:account AND author.role='TEACHER'";
        var params=Map.of("account",c.accountId());
        long courses=jdbc.queryForObject("SELECT COUNT(*) FROM tbl_public_course p JOIN tbl_account author ON author.id=p.author_id WHERE p.status='PENDING_REVIEW' AND "+scope,params,Long.class);
        long problems=jdbc.queryForObject("SELECT COUNT(*) FROM tbl_public_problem p JOIN tbl_account author ON author.id=p.author_id WHERE p.status='PENDING_REVIEW' AND "+scope,params,Long.class);
        return new Contributions(courses,problems);
    }
    @Override public Preview<Activity> activities(Context c) {
        return preview("SELECT id,action,created_at,module FROM ("+AUDIT_EVENTS+") events",c,
                (rs,row)->new Activity(rs.getString("id"),rs.getString("module"),rs.getString("action"),rs.getString("action"),instant(rs,"created_at")));
    }
    @Override public Preview<Audit> audits(Context c) {
        return preview("SELECT id,action,outcome,created_at FROM ("+AUDIT_EVENTS+") events",c,
                (rs,row)->new Audit(rs.getString("id"),rs.getString("action"),rs.getString("outcome"),instant(rs,"created_at")));
    }
    private static Instant instant(java.sql.ResultSet rs,String column) throws java.sql.SQLException {
        Timestamp value=rs.getTimestamp(column);return value==null ? null : value.toInstant();
    }

    @Override public Preview<Notification> notifications(Context c) {
        var params = Map.of("account", c.accountId(), "limit", Math.min(c.limit(),100));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM tbl_notification WHERE recipient_id=:account", params, Long.class);
        var items = jdbc.query("SELECT n.*, c.name AS classroom_name, "
                + "COALESCE(i.classroom_id,a.classroom_id,asg.classroom_id) AS classroom_id "
                + "FROM tbl_notification n "
                + "LEFT JOIN tbl_classroom_invitation i ON i.id=n.classroom_invitation_id "
                + "LEFT JOIN tbl_announcement a ON a.id=n.announcement_id "
                + "LEFT JOIN tbl_assignment asg ON asg.id=n.assignment_id "
                + "LEFT JOIN tbl_classroom c ON c.id=COALESCE(i.classroom_id,a.classroom_id,asg.classroom_id) "
                + "WHERE n.recipient_id=:account ORDER BY n.created_at DESC, n.id LIMIT :limit", params,
                (rs, row) -> {
                    var readAt = rs.getTimestamp("read_at");
                    UUID invitationId = rs.getObject("classroom_invitation_id", UUID.class);
                    UUID announcementId = rs.getObject("announcement_id", UUID.class);
                    UUID assignmentId = rs.getObject("assignment_id", UUID.class);
                    UUID classroomId = rs.getObject("classroom_id", UUID.class);
                    String type = rs.getString("type");
                    String targetType = invitationId != null ? "CLASS_INVITATION"
                            : announcementId != null ? "ANNOUNCEMENT" : assignmentId != null ? "ASSIGNMENT" : "SYSTEM";
                    UUID targetId = invitationId != null ? invitationId : announcementId != null ? announcementId : assignmentId;
                    String actionUrl = classroomId == null ? null : "/classrooms/" + classroomId
                            + (announcementId != null ? "/announcements/" + announcementId
                            : assignmentId != null ? "/assignments/" + assignmentId : "");
                    String title = rs.getString("title");
                    if (title == null) title = invitationId != null ? "Lời mời tham gia " + rs.getString("classroom_name") : type;
                    return new Notification(rs.getObject("id", UUID.class), title,
                            rs.getTimestamp("created_at").toInstant(), readAt == null ? null : readAt.toInstant(),
                            type, rs.getString("message"), targetType, targetId, classroomId, actionUrl);
                });
        return new Preview<>(items, total, total > items.size());
    }
}
