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

/** Persisted account/classroom summaries. Modules without storage return empty sections, never demo records. */
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
        return preview("SELECT c.id, c.name, c.created_at, (SELECT COUNT(*) FROM tbl_classroom_membership m WHERE m.classroom_id=c.id AND m.status='ACTIVE') AS students FROM tbl_classroom c WHERE c.owner_id=:account AND c.status='ACTIVE'", c,
                (rs, row) -> new ClassStatistics(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("students"), 0, 0, 0));
    }
    @Override public Totals totals() {
        return new Totals(count("SELECT COUNT(*) FROM tbl_account WHERE role='STUDENT'"),
                count("SELECT COUNT(*) FROM tbl_account WHERE role='TEACHER'"), count("SELECT COUNT(*) FROM tbl_classroom"), 0, 0, 0);
    }
    @Override public Moderation moderation(Context c) {
        long locked = jdbc.queryForObject("SELECT COUNT(*) FROM tbl_account WHERE login_locked_until>:now", Map.of("now", Timestamp.from(c.now())), Long.class);
        return new Moderation(locked, count("SELECT COUNT(*) FROM tbl_account WHERE status='SUSPENDED'"), 0);
    }
    @Override public List<ChartEvent> chartEvents(Instant from, Instant to) {
        return jdbc.query("SELECT created_at, role AS metric FROM tbl_account WHERE role IN ('STUDENT','TEACHER') AND created_at>=:from AND created_at<:to UNION ALL SELECT created_at, 'CLASSROOM' AS metric FROM tbl_classroom WHERE created_at>=:from AND created_at<:to",
                Map.of("from", Timestamp.from(from), "to", Timestamp.from(to)),
                (rs, row) -> new ChartEvent(rs.getTimestamp("created_at").toInstant(), rs.getString("metric"), 1));
    }
    private <T> Preview<T> preview(String scopedQuery, Context c, RowMapper<T> mapper) {
        var params = Map.of("account", c.accountId(), "limit", c.limit());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + scopedQuery + ") scoped", params, Long.class);
        var items = jdbc.query(scopedQuery + " ORDER BY created_at DESC, id LIMIT :limit", params, mapper);
        return new Preview<>(items, total, total > items.size());
    }
    private long count(String sql) { return jdbc.queryForObject(sql, Map.of(), Long.class); }
    private static <T> Preview<T> empty() { return new Preview<>(List.of(), 0, false); }
    @Override public Preview<Assignment> studentAssignments(Context c) { return empty(); }
    @Override public Preview<Assignment> teacherAssignments(Context c) { return empty(); }
    @Override public Preview<Quiz> studentQuizzes(Context c) { return empty(); }
    @Override public Preview<Course> courses(Context c) { return empty(); }
    @Override public Preview<Notification> notifications(Context c) {
        var params = Map.of("account", c.accountId(), "limit", c.limit());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM tbl_notification WHERE recipient_id=:account", params, Long.class);
        var items = jdbc.query("SELECT n.id, c.name, n.created_at, n.read_at FROM tbl_notification n "
                + "JOIN tbl_classroom_invitation i ON i.id=n.classroom_invitation_id "
                + "JOIN tbl_classroom c ON c.id=i.classroom_id "
                + "WHERE n.recipient_id=:account ORDER BY n.created_at DESC, n.id LIMIT :limit", params,
                (rs, row) -> {
                    var readAt = rs.getTimestamp("read_at");
                    return new Notification(rs.getObject("id", UUID.class),
                            "Lời mời tham gia " + rs.getString("name"),
                            rs.getTimestamp("created_at").toInstant(), readAt == null ? null : readAt.toInstant());
                });
        return new Preview<>(items, total, total > items.size());
    }
    @Override public Preview<Submission> ungraded(Context c) { return empty(); }
    @Override public Preview<Submission> recentSubmissions(Context c) { return empty(); }
    @Override public Contributions contributions(Context c, boolean allTeachers) { return new Contributions(0, 0); }
    @Override public Preview<Activity> activities(Context c) { return empty(); }
    @Override public Preview<Audit> audits(Context c) { return empty(); }
}
