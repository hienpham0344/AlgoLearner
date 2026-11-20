package com.algolearner.platform.course;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository
@Profile("!mock")
@RequiredArgsConstructor
public class PublicCourseRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<CourseDtos.Summary> SUMMARY = (row, index) -> new CourseDtos.Summary(
            row.getObject("id",UUID.class),row.getObject("author_id",UUID.class),row.getString("title"),
            row.getString("description"),row.getString("status"),row.getTimestamp("created_at").toInstant());
    private static final RowMapper<CourseDtos.Unit> UNIT = (row, index) -> new CourseDtos.Unit(
            row.getObject("id",UUID.class),row.getString("title"),row.getString("content"),row.getInt("display_order"));

    public Optional<CourseDtos.Summary> find(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM tbl_public_course WHERE id=?" + (lock ? " FOR UPDATE" : ""),SUMMARY,id).stream().findFirst();
    }

    public CourseDtos.Summary create(UUID author, CourseDtos.Create request) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_public_course(id,author_id,title,description) VALUES (?,?,?,?)",id,author,request.title().strip(),request.description());
        return find(id,false).orElseThrow();
    }

    public int unitCount(UUID course) {
        return jdbc.queryForObject("SELECT count(*) FROM tbl_public_course_unit WHERE course_id=?",Integer.class,course);
    }

    public CourseDtos.Unit addUnit(UUID course, CourseDtos.UnitRequest request) {
        UUID id = UUID.randomUUID();
        int order = unitCount(course);
        jdbc.update("INSERT INTO tbl_public_course_unit(id,course_id,title,content,display_order) VALUES (?,?,?,?,?)",
                id,course,request.title().strip(),request.content(),order);
        return new CourseDtos.Unit(id,request.title().strip(),request.content(),order);
    }

    public void changeStatus(UUID course, String state, String reason) {
        jdbc.update("UPDATE tbl_public_course SET status=?,review_reason=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",state,reason,course);
    }

    public List<CourseDtos.Unit> units(UUID course) {
        return jdbc.query("SELECT * FROM tbl_public_course_unit WHERE course_id=? ORDER BY display_order,id",UNIT,course);
    }

    public List<CourseDtos.Summary> list(String status, UUID author, int page, int size) {
        String authorScope = author == null ? "" : " AND author_id=?";
        String sql = "SELECT * FROM tbl_public_course WHERE status=?"+authorScope+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?";
        return author == null ? jdbc.query(sql,SUMMARY,status,size,(long)page*size)
                : jdbc.query(sql,SUMMARY,status,author,size,(long)page*size);
    }

    public long count(String status, UUID author) {
        return author == null ? jdbc.queryForObject("SELECT count(*) FROM tbl_public_course WHERE status=?",Long.class,status)
                : jdbc.queryForObject("SELECT count(*) FROM tbl_public_course WHERE status=? AND author_id=?",Long.class,status,author);
    }

    public void enroll(UUID account, UUID course) {
        jdbc.update("INSERT INTO tbl_course_enrollment(id,course_id,account_id) VALUES (?,?,?) ON CONFLICT(course_id,account_id) DO NOTHING",
                UUID.randomUUID(),course,account);
    }

    public Optional<UUID> enrollment(UUID account, UUID course) {
        return jdbc.query("SELECT id FROM tbl_course_enrollment WHERE account_id=? AND course_id=?",
                (row,index)->row.getObject("id",UUID.class),account,course).stream().findFirst();
    }

    public boolean containsUnit(UUID course, UUID unit) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM tbl_public_course_unit WHERE id=? AND course_id=?)",Boolean.class,unit,course));
    }

    public void complete(UUID enrollment, UUID unit) {
        jdbc.update("INSERT INTO tbl_course_unit_completion(id,enrollment_id,unit_id) VALUES (?,?,?) ON CONFLICT(enrollment_id,unit_id) DO NOTHING",
                UUID.randomUUID(),enrollment,unit);
    }

    public int completedUnits(UUID enrollment, UUID course) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM tbl_course_unit_completion c JOIN tbl_public_course_unit u ON u.id=c.unit_id
                WHERE c.enrollment_id=? AND u.course_id=?
                """,Integer.class,enrollment,course);
    }
}
