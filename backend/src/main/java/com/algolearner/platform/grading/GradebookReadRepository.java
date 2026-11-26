package com.algolearner.platform.grading;

import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import com.algolearner.platform.grading.GradebookDtos.GradeItem;
import com.algolearner.platform.grading.GradebookDtos.ItemReference;
import com.algolearner.platform.grading.GradebookDtos.StudentIdentity;
import com.algolearner.platform.grading.GradebookDtos.SubmissionAnswer;
import com.algolearner.platform.grading.GradebookDtos.TeacherSubmissionDetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mock")
@RequiredArgsConstructor
public class GradebookReadRepository {
    private static final String SUBMISSIONS = """
            WITH scoped AS (
                SELECT t.id,t.student_id,t.submitted_at,t.is_late AS late,t.status
                FROM tbl_assignment a JOIN tbl_assignment_attempt t ON t.assignment_id=a.id
                WHERE :sourceType='ASSIGNMENT' AND a.classroom_id=:classroomId AND a.id=:itemId
                    AND t.submitted_at IS NOT NULL AND t.status IN ('SUBMITTED','GRADED')
                UNION ALL
                SELECT t.id,t.student_id,t.submitted_at,t.late,t.status
                FROM tbl_quiz q JOIN tbl_quiz_attempt t ON t.quiz_id=q.id
                WHERE :sourceType='QUIZ' AND q.classroom_id=:classroomId AND q.id=:itemId AND t.status='SUBMITTED'
            )
            """;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    List<GradeItem> items(UUID classroomId) {
        return jdbc.query("""
                SELECT 'ASSIGNMENT' AS source_type,id,title FROM tbl_assignment WHERE classroom_id=:classroomId
                UNION ALL
                SELECT 'QUIZ' AS source_type,id,title FROM tbl_quiz WHERE classroom_id=:classroomId
                ORDER BY source_type,id
                """, Map.of("classroomId", classroomId), (row, index) -> new GradeItem(
                        SourceType.valueOf(row.getString("source_type")), row.getObject("id", UUID.class), row.getString("title")));
    }

    List<ItemReference> ungradedItems(UUID classroomId) {
        return jdbc.query("""
                SELECT 'ASSIGNMENT' AS source_type,id FROM tbl_assignment
                WHERE classroom_id=:classroomId AND type='UNGRADED'
                UNION ALL
                SELECT 'QUIZ' AS source_type,id FROM tbl_quiz WHERE classroom_id=:classroomId AND NOT graded
                """, Map.of("classroomId", classroomId), (row, index) -> new ItemReference(
                        SourceType.valueOf(row.getString("source_type")), row.getObject("id", UUID.class)));
    }

    List<ItemReference> hiddenItems(UUID classroomId) {
        return jdbc.query("""
                SELECT li.item_type,li.item_id FROM tbl_lesson_linked_item li
                JOIN tbl_lesson l ON l.id=li.lesson_id
                WHERE l.classroom_id=:classroomId AND li.item_type IN ('ASSIGNMENT','QUIZ')
                GROUP BY li.item_type,li.item_id HAVING NOT bool_or(l.status='PUBLISHED')
                """, Map.of("classroomId", classroomId), (row, index) -> new ItemReference(
                        SourceType.valueOf(row.getString("item_type")), row.getObject("item_id", UUID.class)));
    }

    Page<StudentIdentity> roster(UUID classroomId, Pageable page) {
        var parameters = Map.of("classroomId", classroomId, "limit", page.getPageSize(), "offset", page.getOffset());
        var total = jdbc.queryForObject("""
                SELECT count(*) FROM tbl_classroom_membership WHERE classroom_id=:classroomId AND status='ACTIVE'
                """, parameters, Long.class);
        var rows = jdbc.query("""
                SELECT a.id,a.username FROM tbl_classroom_membership m JOIN tbl_account a ON a.id=m.student_id
                WHERE m.classroom_id=:classroomId AND m.status='ACTIVE'
                ORDER BY a.id LIMIT :limit OFFSET :offset
                """, parameters, (row, index) -> new StudentIdentity(row.getObject("id", UUID.class), row.getString("username")));
        return new PageImpl<>(rows, page, total);
    }

    StudentIdentity student(UUID classroomId, UUID studentId) {
        return jdbc.queryForObject("""
                SELECT a.id,a.username FROM tbl_classroom_membership m JOIN tbl_account a ON a.id=m.student_id
                WHERE m.classroom_id=:classroomId AND m.student_id=:studentId AND m.status='ACTIVE'
                """, Map.of("classroomId", classroomId, "studentId", studentId),
                (row, index) -> new StudentIdentity(row.getObject("id", UUID.class), row.getString("username")));
    }

    Page<TeacherSubmissionDetail> submissions(UUID classroomId, SourceType type, UUID itemId, Pageable page) {
        var parameters = Map.of("classroomId", classroomId, "sourceType", type.name(), "itemId", itemId,
                "limit", page.getPageSize(), "offset", page.getOffset());
        var total = jdbc.queryForObject(SUBMISSIONS + "SELECT count(*) FROM scoped", parameters, Long.class);
        var rows = jdbc.query(SUBMISSIONS + """
                SELECT * FROM scoped ORDER BY submitted_at DESC,id LIMIT :limit OFFSET :offset
                """, parameters, (row, index) -> new TeacherSubmissionDetail(
                        row.getObject("id", UUID.class), row.getObject("student_id", UUID.class),
                        row.getObject("submitted_at", OffsetDateTime.class).toInstant(), row.getBoolean("late"),
                        row.getString("status"), List.of()));
        if (rows.isEmpty()) return new PageImpl<>(rows, page, total);
        var attempts = rows.stream().map(TeacherSubmissionDetail::attemptId).toList();
        var answerRows = jdbc.query(SUBMISSIONS + """
                SELECT a.attempt_id,a.question_id,a.selected_option_ids::text AS options,a.text_answer,a.file_url,a.code_submission
                FROM scoped s JOIN tbl_assignment_answer a ON a.attempt_id=s.id
                WHERE :sourceType='ASSIGNMENT' AND s.id IN (:attempts)
                UNION ALL
                SELECT a.attempt_id,a.question_id,a.selected_options::text,NULL,NULL,NULL
                FROM scoped s JOIN tbl_quiz_answer a ON a.attempt_id=s.id
                WHERE :sourceType='QUIZ' AND s.id IN (:attempts)
                ORDER BY question_id
                """, Map.of("classroomId", classroomId, "sourceType", type.name(), "itemId", itemId, "attempts", attempts),
                (row, index) -> Map.entry(row.getObject("attempt_id", UUID.class), new SubmissionAnswer(
                        row.getObject("question_id", UUID.class), options(row.getString("options")), row.getString("text_answer"),
                        row.getString("file_url"), row.getString("code_submission"))));
        var answers = answerRows.stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return new PageImpl<>(rows.stream().map(row -> new TeacherSubmissionDetail(row.attemptId(), row.studentId(),
                row.submittedAt(), row.late(), row.status(), answers.getOrDefault(row.attemptId(), List.of()))).toList(), page, total);
    }

    private JsonNode options(String value) {
        if (value == null) return null;
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored submission options", exception);
        }
    }
}
