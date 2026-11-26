package com.algolearner.platform.grading;

import com.algolearner.platform.grading.GradebookDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mock")
@RequiredArgsConstructor
public class GradebookPublicationRepository {
    record Receipt(UUID actor, GradePublicationRequest request, GradePublicationResponse response) { }
    record Snapshot(UUID id, UUID publicationId, UUID schemeId, Overall overall, Set<ItemReference> contributors) { }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    Optional<Receipt> receipt(UUID gradebook, String key) {
        return jdbc.query("""
                SELECT * FROM tbl_gradebook_publication WHERE gradebook_id=? AND idempotency_key=?
                """, (r, n) -> new Receipt(r.getObject("actor_id", UUID.class),
                new GradePublicationRequest(PublicationScope.valueOf(r.getString("scope")),
                        r.getObject("target_student_id", UUID.class), r.getLong("expected_version")),
                new GradePublicationResponse(r.getObject("id", UUID.class), r.getObject("scheme_revision_id", UUID.class),
                        r.getLong("applied_version"), r.getInt("published_count"), r.getInt("ineligible_count"),
                        r.getTimestamp("created_at").toInstant())), gradebook, key).stream().findFirst();
    }

    void insertReceipt(UUID gradebook, String key, Receipt receipt) {
        var request = receipt.request();
        var response = receipt.response();
        jdbc.update("""
                INSERT INTO tbl_gradebook_publication(id,gradebook_id,scheme_revision_id,actor_id,scope,target_student_id,
                    idempotency_key,expected_version,applied_version,published_count,ineligible_count,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, response.publicationId(), gradebook, response.schemeRevisionId(), receipt.actor(),
                request.scope().name(), request.studentId(), key, request.expectedVersion(), response.version(),
                response.publishedCount(), response.ineligibleCount(), Timestamp.from(response.publishedAt()));
    }

    void advanceVersion(UUID gradebook) {
        jdbc.update("UPDATE tbl_gradebook SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", gradebook);
    }

    Optional<Snapshot> current(UUID gradebook, UUID student) {
        return jdbc.query("""
                SELECT r.id,r.publication_id,r.scheme_revision_id,r.overall::text,r.contributors::text
                FROM tbl_gradebook_published_student p JOIN tbl_gradebook_student_result r ON r.id=p.result_id
                WHERE p.gradebook_id=? AND p.student_id=?
                """, (r, n) -> new Snapshot(r.getObject(1, UUID.class), r.getObject(2, UUID.class),
                r.getObject(3, UUID.class), overall(r.getString(4)), contributors(r.getString(5))), gradebook, student).stream().findFirst();
    }

    UUID append(UUID gradebook, UUID student, UUID publication, UUID scheme, GradeCalculator.Calculation grade) {
        var result = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_gradebook_student_result(id,gradebook_id,student_id,publication_id,scheme_revision_id,overall,contributors)
                VALUES (?,?,?,?,?,?::jsonb,?::jsonb)
                """, result, gradebook, student, publication, scheme, encode(grade.overall()), encode(grade.contributors()));
        jdbc.update("""
                INSERT INTO tbl_gradebook_published_student(gradebook_id,student_id,result_id) VALUES (?,?,?)
                ON CONFLICT (gradebook_id,student_id) DO UPDATE
                SET result_id=excluded.result_id,updated_at=CURRENT_TIMESTAMP
                """, gradebook, student, result);
        return result;
    }

    List<UUID> publishedStudents(UUID gradebook) {
        return jdbc.query("""
                SELECT student_id FROM tbl_gradebook_published_student WHERE gradebook_id=? ORDER BY student_id
                """, (r, n) -> r.getObject(1, UUID.class), gradebook);
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize published grade", error);
        }
    }

    private Set<ItemReference> contributors(String value) {
        if (value == null) return null;
        try {
            List<ItemReference> items = json.readerForListOf(ItemReference.class).readValue(value);
            return Set.copyOf(items);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot read published grade contributors", error);
        }
    }

    private Overall overall(String value) {
        try {
            return json.readValue(value, Overall.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot read published grade", error);
        }
    }
}
