package com.algolearner.platform.grading;

import com.algolearner.platform.grading.GradebookDtos.GradebookHistoryEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mock")
@RequiredArgsConstructor
public class GradebookHistoryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    void append(UUID gradebook, UUID student, UUID actor, UUID scheme, String action, String reason, Object before, Object after) {
        jdbc.update("""
                INSERT INTO tbl_gradebook_history(gradebook_id,student_id,actor_id,scheme_revision_id,action,
                    reason,previous_value,current_value) VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb)
                """, gradebook, student, actor, scheme, action, reason, encode(before), encode(after));
    }

    Page<GradebookHistoryEntry> list(UUID classroom, Pageable page) {
        var total = jdbc.queryForObject("""
                SELECT count(*) FROM tbl_gradebook_history h JOIN tbl_gradebook b ON b.id=h.gradebook_id
                WHERE b.classroom_id=?
                """, Long.class, classroom);
        var rows = jdbc.query("""
                SELECT h.* FROM tbl_gradebook_history h JOIN tbl_gradebook b ON b.id=h.gradebook_id
                WHERE b.classroom_id=? ORDER BY h.id DESC LIMIT ? OFFSET ?
                """, (row, index) -> new GradebookHistoryEntry(row.getLong("id"), row.getObject("student_id", UUID.class),
                row.getObject("actor_id", UUID.class), row.getObject("scheme_revision_id", UUID.class), row.getString("action"),
                row.getString("reason"), decode(row.getString("previous_value")), decode(row.getString("current_value")),
                row.getTimestamp("created_at").toInstant()), classroom, page.getPageSize(), page.getOffset());
        return new PageImpl<>(rows, page, total);
    }

    String encode(Object value) {
        if (value == null) return null;
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize gradebook history", error);
        }
    }

    private JsonNode decode(String value) {
        if (value == null) return null;
        try {
            return json.readTree(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot read gradebook history", error);
        }
    }
}
