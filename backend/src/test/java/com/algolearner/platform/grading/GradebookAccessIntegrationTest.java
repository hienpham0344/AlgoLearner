package com.algolearner.platform.grading;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=gradebook-test-only-secret-at-least-32-bytes",
        "app.gradebook.export.max-students=101"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class GradebookAccessIntegrationTest extends PostgresIntegrationTest {
    @Autowired GradebookAccess access;
    @Autowired GradebookSchemeService schemeService;
    @Autowired GradebookRefreshRepository gradebookQueue;
    @Autowired GradebookRefreshService gradebookRefresh;
    @Autowired GradebookPublicationService publicationService;
    @Autowired GradebookVisibilityService gradebookVisibility;
    @Autowired com.algolearner.platform.quiz.QuizService quizService;
    @Autowired ClassroomService classrooms;
    @Autowired AccountRepository accounts;
    @Autowired GradebookRepository gradebooks;
    @Autowired GradeSchemeRevisionRepository revisions;
    @Autowired GradeSchemeItemRepository items;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired MockMvc mvc;

    @Test
    void hiddenLessonItemSuppressesOverallUntilRefreshAndIsExcludedFromNewScheme() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroomId = classroom(teacher);
        classrooms.directAdd(teacher.getId(), classroomId, new ClassroomDtos.DirectAdd(student.getUsername()));
        var quiz = publishedQuiz(classroomId, student.getId(), 75);
        var otherItem = publishedQuiz(classroomId, student.getId(), 50);
        var oldItems = List.of(new GradebookDtos.SchemeItem(SourceType.QUIZ, quiz, null, true),
                new GradebookDtos.SchemeItem(SourceType.QUIZ, otherItem, null, true));
        var auth = principal(teacher);
        var old = schemeService.apply(auth, classroomId, new GradebookDtos.SchemeApplyRequest(
                GradeSchemeRevisionEntity.Mode.RAW, 0L, oldItems, null));
        publicationService.publish(auth, classroomId, "hidden-item", new GradebookDtos.GradePublicationRequest(
                GradebookDtos.PublicationScope.CLASS, null, 1L));
        var base = "/api/v1/classrooms/" + classroomId + "/gradebook";
        mvc.perform(put(base + "/visibility").with(authentication(auth)).contentType("application/json")
                .content("{\"showOverall\":true,\"expectedVersion\":2}")).andExpect(status().isOk());
        var lesson = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_lesson(id,classroom_id,title,status) VALUES (?,?,'Hidden lesson','ARCHIVED')", lesson, classroomId);
        jdbc.update("INSERT INTO tbl_lesson_linked_item(id,lesson_id,item_type,item_id) VALUES (?,?,'QUIZ',?)",
                UUID.randomUUID(), lesson, quiz);
        assertThatThrownBy(() -> schemeService.apply(auth, classroomId,
                new GradebookDtos.SchemeApplyRequest(GradeSchemeRevisionEntity.Mode.RAW, 3L, oldItems, null)))
                .isInstanceOf(AppException.class).hasMessageContaining("hidden classroom item");
        assertThat(schemeService.get(auth, classroomId).revisionId()).isEqualTo(old.revisionId());
        var studentAuth = authentication(principal(student));
        mvc.perform(get(base + "/me").with(studentAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].sourceId").value(otherItem.toString()))
                .andExpect(jsonPath("$.data.items[0].earnedPoints").value(50))
                .andExpect(jsonPath("$.data.items[0].possiblePoints").value(100))
                .andExpect(jsonPath("$.data.overall").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.overallStatus").value("UPDATING"));
        var work = gradebookQueue.due(100).stream().filter(row -> row.classroomId().equals(classroomId))
                .findFirst().orElseThrow();
        jdbc.execute("ALTER TABLE tbl_gradebook_history ADD CONSTRAINT test_stale_refresh_failure CHECK (student_id <> '"
                + student.getId() + "'::uuid) NOT VALID");
        try {
            gradebookRefresh.process(work);
            exhaustRefreshRetries(work);
        } finally {
            jdbc.execute("ALTER TABLE tbl_gradebook_history DROP CONSTRAINT test_stale_refresh_failure");
        }
        mvc.perform(get(base + "/me").with(studentAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallStatus").value("UPDATING"));
        assertRefreshReset(work);
        gradebookRefresh.process(work);
        mvc.perform(get(base + "/me").with(studentAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallStatus").doesNotExist())
                .andExpect(jsonPath("$.data.overall.current.earnedPoints").value(50))
                .andExpect(jsonPath("$.data.overall.current.possiblePoints").value(100))
                .andExpect(jsonPath("$.data.overall.finalGrade").doesNotExist());
        schemeService.apply(auth, classroomId, new GradebookDtos.SchemeApplyRequest(
                GradeSchemeRevisionEntity.Mode.RAW, 3L, List.of(oldItems.getLast()), null));
        mvc.perform(get(base + "/me").with(authentication(principal(student)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.finalGrade.percentage").value(50));
        assertThat(jdbc.queryForObject("""
                SELECT (overall->'finalGrade'->>'percentage')::numeric FROM tbl_gradebook_student_result
                WHERE scheme_revision_id=? AND student_id=? AND overall->'finalGrade'->>'percentage' IS NOT NULL
                """, BigDecimal.class, old.revisionId(), student.getId())).isEqualByComparingTo("62.5");
    }

    @Test
    void teacherSubmissionDetailsIncludeScopedCodeEssayAndQuizAnswers() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var foreign = account(AccountRole.TEACHER);
        var classroomId = classroom(teacher);
        classrooms.directAdd(teacher.getId(), classroomId, new ClassroomDtos.DirectAdd(student.getUsername()));
        var assignment = UUID.randomUUID();
        var attempt = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_assignment(id,classroom_id,title,status) VALUES (?,?,'Exercise','PUBLISHED')", assignment, classroomId);
        jdbc.update("""
                INSERT INTO tbl_assignment_attempt(id,assignment_id,student_id,attempt_number,status,started_at,submitted_at)
                VALUES (?,?,?,1,'SUBMITTED',now(),now())
                """, attempt, assignment, student.getId());
        for (var type : List.of("CODE", "ESSAY")) {
            var question = UUID.randomUUID();
            jdbc.update("INSERT INTO tbl_assignment_question(id,assignment_id,question_type,title,prompt) VALUES (?,?,?,'Task','Answer')",
                    question, assignment, type);
            jdbc.update("""
                    INSERT INTO tbl_assignment_answer(id,attempt_id,question_id,code_submission,text_answer,is_draft)
                    VALUES (?,?,?,?,?,false)
                    """, UUID.randomUUID(), attempt, question, type.equals("CODE") ? "print(42)" : null,
                    type.equals("ESSAY") ? "An explanation" : null);
        }
        var quiz = publishedQuiz(classroomId, student.getId(), 80);
        var selected = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_quiz_answer(id,attempt_id,question_id,selected_options)
                SELECT ?,id,?,jsonb_build_array(?::text) FROM tbl_quiz_attempt WHERE quiz_id=?
                """, UUID.randomUUID(), UUID.randomUUID(), selected.toString(), quiz);
        var base = "/api/v1/classrooms/" + classroomId + "/gradebook/items/{item}/submissions";
        mvc.perform(get(base, assignment).param("sourceType", "ASSIGNMENT").with(authentication(principal(teacher))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].answers[*].code", org.hamcrest.Matchers.hasItem("print(42)")))
                .andExpect(jsonPath("$.data.items[0].answers[*].essay", org.hamcrest.Matchers.hasItem("An explanation")));
        mvc.perform(get(base, quiz).param("sourceType", "QUIZ").with(authentication(principal(teacher))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].answers[0].selectedOptionIds[0]").value(selected.toString()));
        mvc.perform(get(base, assignment).param("sourceType", "ASSIGNMENT").with(authentication(principal(foreign))))
                .andExpect(status().isForbidden());
        mvc.perform(get(base, quiz).param("sourceType", "QUIZ").with(authentication(principal(student))))
                .andExpect(status().isForbidden());
    }

    @Test
    void simultaneousApplyAndPublishOnlyOneCanUseTheSameVersion() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroomId = classroom(teacher);
        classrooms.directAdd(teacher.getId(), classroomId, new ClassroomDtos.DirectAdd(student.getUsername()));
        var item = publishedQuiz(classroomId, student.getId(), 75);
        var configured = List.of(new GradebookDtos.SchemeItem(SourceType.QUIZ, item, new BigDecimal("100"), true));
        var auth = principal(teacher);
        schemeService.apply(auth, classroomId, new GradebookDtos.SchemeApplyRequest(
                GradeSchemeRevisionEntity.Mode.RAW, 0L, configured, null));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var apply = executor.submit(() -> contestVersion(barrier, () -> schemeService.apply(auth, classroomId,
                    new GradebookDtos.SchemeApplyRequest(GradeSchemeRevisionEntity.Mode.WEIGHTED, 1L, configured, null))));
            var publish = executor.submit(() -> contestVersion(barrier, () -> publicationService.publish(auth, classroomId,
                    "race", new GradebookDtos.GradePublicationRequest(GradebookDtos.PublicationScope.CLASS, null, 1L))));
            assertThat(List.of(apply.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    publish.get(20, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMMITTED", "GRADEBOOK_VERSION_CONFLICT");
        }
        assertThat(gradebook(classroomId).getVersion()).isEqualTo(2);
    }

    private String contestVersion(java.util.concurrent.CyclicBarrier barrier, Runnable operation) throws Exception {
        barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
        try {
            operation.run();
            return "COMMITTED";
        } catch (AppException error) {
            return error.getErrorCode();
        }
    }

    @Test
    void exportsRealCsvAndXlsxWithScopedGradesAndEnforcesLimitsBeforeAttachment() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var otherTeacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroomId = classroom(teacher);
        classrooms.directAdd(teacher.getId(), classroomId, new ClassroomDtos.DirectAdd(student.getUsername()));
        var quiz = publishedQuiz(classroomId, student.getId(), 75);
        jdbc.update("UPDATE tbl_quiz SET title='=formula' WHERE id=?", quiz);
        jdbc.update("UPDATE tbl_quiz_attempt SET grade_published=false WHERE quiz_id=?", quiz);
        var base = "/api/v1/classrooms/" + classroomId + "/gradebook";
        var teacherAuth = authentication(principal(teacher));
        schemeService.apply(principal(teacher), classroomId, new GradebookDtos.SchemeApplyRequest(
                GradeSchemeRevisionEntity.Mode.RAW, 0L,
                List.of(new GradebookDtos.SchemeItem(SourceType.QUIZ, quiz, null, true)), null));
        var csv = mvc.perform(get(base + "/export").param("format", "csv").with(teacherAuth))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse();
        try (var parser = org.apache.commons.csv.CSVParser.parse(csv.getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                org.apache.commons.csv.CSVFormat.RFC4180)) {
            var records = parser.getRecords();
            assertThat(records).hasSize(2);
            assertThat(records.getFirst().get(4)).startsWith("'=formula");
            assertThat(records.getLast().get(2)).isEqualTo("75.0000");
            assertThat(records.getLast().get(4)).isEqualTo("75.0000");
            assertThat(records.getLast().get(7)).isEqualTo("false");
        }
        var xlsx = mvc.perform(get(base + "/export").param("format", "xlsx").with(teacherAuth))
                .andExpect(status().isOk()).andReturn().getResponse();
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(xlsx.getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(4).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(sheet.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(75);
            assertThat(sheet.getRow(1).getCell(7).getBooleanCellValue()).isFalse();
        }
        mvc.perform(get(base + "/export").param("format", "csv").with(authentication(principal(otherTeacher))))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Content-Disposition"));
        jdbc.update("""
                INSERT INTO tbl_assignment(id,classroom_id,title)
                SELECT gen_random_uuid(),?,'Extra item' FROM generate_series(1,200)
                """, classroomId);
        mvc.perform(get(base + "/export").param("format", "csv").with(teacherAuth)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("GRADEBOOK_EXPORT_LIMIT_EXCEEDED"))
                .andExpect(header().doesNotExist("Content-Disposition"));
        assertThat(gradebook(classroomId).getVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM tbl_audit_log WHERE classroom_id=? AND action='GRADEBOOK_EXPORTED'
                """, Long.class, classroomId)).isEqualTo(2L);
    }

    @Test
    void recalculationUpdatesPublishedGradeHistoryAndNotificationsAtomically() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroomId = classroom(teacher);
        classrooms.directAdd(teacher.getId(), classroomId, new ClassroomDtos.DirectAdd(student.getUsername()));
        var fullScore = publishedQuiz(classroomId, student.getId(), 100);
        var halfScore = publishedQuiz(classroomId, student.getId(), 50);
        var submission = jdbc.queryForObject("SELECT id FROM tbl_quiz_attempt WHERE quiz_id=?", UUID.class, fullScore);
        var override = jdbc.queryForObject("""
                INSERT INTO tbl_quiz_grade_override(quiz_id,student_id,attempt_id,actor_id,original_score,final_score,reason)
                VALUES (?,?,?,?,100,100,'Preserved override') RETURNING id
                """, Long.class, fullScore, student.getId(), submission, teacher.getId());
        var submissionBefore = jdbc.queryForObject("SELECT to_jsonb(a)::text FROM tbl_quiz_attempt a WHERE id=?", String.class, submission);
        var overrideBefore = jdbc.queryForObject("SELECT to_jsonb(g)::text FROM tbl_quiz_grade_override g WHERE id=?", String.class, override);
        var gradebookId = gradebook(classroomId).getId();
        var teacherAuth = authentication(principal(teacher));
        var studentAuth = authentication(principal(student));
        var base = "/api/v1/classrooms/" + classroomId + "/gradebook";
        var mode = GradeSchemeRevisionEntity.Mode.WEIGHTED;
        schemeService.apply(principal(teacher), classroomId, new GradebookDtos.SchemeApplyRequest(mode, 0L,
                List.of(new GradebookDtos.SchemeItem(SourceType.QUIZ, fullScore, new BigDecimal("20"), true),
                        new GradebookDtos.SchemeItem(SourceType.QUIZ, halfScore, new BigDecimal("80"), true)), "Initial"));
        mvc.perform(post(base + "/publications").header("Idempotency-Key", "class-publish").with(teacherAuth)
                .contentType("application/json").content("{\"scope\":\"CLASS\",\"expectedVersion\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(put(base + "/visibility").with(teacherAuth).contentType("application/json")
                .content("{\"showOverall\":true,\"expectedVersion\":2}")).andExpect(status().isOk());

        jdbc.update("INSERT INTO tbl_notification(id,recipient_id,type,title) VALUES (?,?,'SYSTEM','Existing notice')",
                UUID.randomUUID(), student.getId());
        mvc.perform(put(base + "/scheme").with(teacherAuth).header("User-Agent", "Gradebook integration test")
                .contentType("application/json").content("""
                        {"mode":"WEIGHTED","expectedVersion":3,"reason":"Changed weights","items":[
                          {"sourceType":"QUIZ","sourceId":"%s","weight":80,"required":true},
                          {"sourceType":"QUIZ","sourceId":"%s","weight":20,"required":true}]}
                        """.formatted(fullScore, halfScore))).andExpect(status().isOk());
        mvc.perform(get(base + "/me").with(studentAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.finalGrade.percentage").value(90));
        mvc.perform(get(base + "/history").with(teacherAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("SCHEME_RECALCULATED"))
                .andExpect(jsonPath("$.data.items[0].previousValue.published.finalGrade.percentage").value(60))
                .andExpect(jsonPath("$.data.items[0].currentValue.published.finalGrade.percentage").value(90));
        mvc.perform(get(base + "/history").with(studentAuth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications").with(studentAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.type == 'GRADEBOOK_FINAL_CHANGED')].actionUrl",
                        org.hamcrest.Matchers.contains("/classrooms/" + classroomId + "/gradebook")))
                .andExpect(jsonPath("$.data.items[?(@.type == 'SYSTEM')].title",
                        org.hamcrest.Matchers.contains("Existing notice")));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM tbl_audit_log WHERE classroom_id=? AND action='GRADEBOOK_SCHEME_APPLIED'
                  AND user_agent='Gradebook integration test' AND client_ip IS NOT NULL
                """, Long.class, classroomId)).isEqualTo(1L);

        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            schemeService.apply(principal(teacher), classroomId,
                    new GradebookDtos.SchemeApplyRequest(mode, 4L,
                            List.of(new GradebookDtos.SchemeItem(SourceType.QUIZ, halfScore, new BigDecimal("100"), true)), "Rollback"));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("force rollback");
        assertThat(gradebook(classroomId).getVersion()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT to_jsonb(a)::text FROM tbl_quiz_attempt a WHERE id=?", String.class, submission))
                .isEqualTo(submissionBefore);
        assertThat(jdbc.queryForObject("SELECT to_jsonb(g)::text FROM tbl_quiz_grade_override g WHERE id=?", String.class, override))
                .isEqualTo(overrideBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_gradebook_student_result WHERE gradebook_id=?",
                Long.class, gradebookId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_gradebook_history WHERE gradebook_id=?",
                Long.class, gradebookId)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_notification WHERE recipient_id=? AND type='GRADEBOOK_FINAL_CHANGED'",
                Long.class, student.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_audit_log WHERE classroom_id=? AND action='GRADEBOOK_SCHEME_APPLIED'",
                Long.class, classroomId)).isEqualTo(2L);

        var joinedLater = account(AccountRole.STUDENT);
        classrooms.directAdd(teacher.getId(), classroomId, new ClassroomDtos.DirectAdd(joinedLater.getUsername()));
        jdbc.update("""
                INSERT INTO tbl_quiz_attempt(id,quiz_id,student_id,status,started_at,submitted_at,score,grade_published,snapshot_json)
                VALUES (?,?,?,'SUBMITTED',now(),now(),100,true,'{}'::jsonb)
                """, UUID.randomUUID(), fullScore, joinedLater.getId());
        mvc.perform(get(base + "/me").with(authentication(principal(joinedLater)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].earnedPoints").value(100))
                .andExpect(jsonPath("$.data.overall").doesNotExist());
    }

    @Test
    void failedRefreshBacksOffAndDoesNotBlockHealthyWork() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var healthy = account(AccountRole.STUDENT);
        var classroom = classroom(teacher);
        for (var member : List.of(student, healthy)) {
            classrooms.directAdd(teacher.getId(), classroom, new ClassroomDtos.DirectAdd(member.getUsername()));
        }
        var quiz = publishedQuiz(classroom, student.getId(), 50);
        jdbc.update("""
                INSERT INTO tbl_quiz_attempt(id,quiz_id,student_id,status,started_at,submitted_at,score,grade_published,snapshot_json)
                VALUES (?,?,?,'SUBMITTED',now(),now(),50,true,'{}'::jsonb)
                """, UUID.randomUUID(), quiz, healthy.getId());
        var gradebook = gradebook(classroom).getId();
        schemeService.apply(principal(teacher), classroom, new GradebookDtos.SchemeApplyRequest(
                GradeSchemeRevisionEntity.Mode.RAW, 0L,
                List.of(new GradebookDtos.SchemeItem(SourceType.QUIZ, quiz, null, true)), null));
        publicationService.publish(principal(teacher), classroom, "worker-retry",
                new GradebookDtos.GradePublicationRequest(GradebookDtos.PublicationScope.CLASS, null, 1L));

        gradebookVisibility.update(principal(teacher), classroom, new GradebookDtos.VisibilityRequest(true, 2L));

        jdbc.update("UPDATE tbl_quiz_attempt SET score=80 WHERE quiz_id=?", quiz);
        gradebookQueue.enqueue(new GradeSourceChanged(classroom, student.getId()));
        gradebookQueue.enqueue(new GradeSourceChanged(classroom, healthy.getId()));
        jdbc.update("""
                UPDATE tbl_gradebook_refresh SET next_attempt_at=now()-interval '1 minute'
                WHERE gradebook_id=? AND student_id=?
                """, gradebook, student.getId());
        jdbc.execute("ALTER TABLE tbl_gradebook_history ADD CONSTRAINT test_refresh_failure CHECK (student_id <> '"
                + student.getId() + "'::uuid) NOT VALID");
        try {
            assertThat(gradebookRefresh.processNext()).isTrue();
            assertThat(jdbc.queryForMap("""
                    SELECT attempt_count,last_error,next_attempt_at > now() AS backed_off
                    FROM tbl_gradebook_refresh WHERE gradebook_id=? AND student_id=?
                    """, gradebook, student.getId()))
                    .containsEntry("attempt_count", 1).containsEntry("backed_off", true)
                    .containsEntry("last_error", "GRADEBOOK_REFRESH_PROCESSING_FAILED");
            var nextAttempt = jdbc.queryForObject("""
                    SELECT next_attempt_at FROM tbl_gradebook_refresh WHERE gradebook_id=? AND student_id=?
                    """, java.time.OffsetDateTime.class, gradebook, student.getId());
            overrideQuizGrade(teacher, classroom, quiz, student, 85);
            assertThat(jdbc.queryForMap("""
                    SELECT attempt_count,last_error,next_attempt_at=? AS same_schedule
                    FROM tbl_gradebook_refresh WHERE gradebook_id=? AND student_id=?
                    """, nextAttempt, gradebook, student.getId()))
                    .containsEntry("attempt_count", 1).containsEntry("same_schedule", true)
                    .containsEntry("last_error", "GRADEBOOK_REFRESH_PROCESSING_FAILED");
            assertThat(gradebookRefresh.processNext()).isTrue();
            assertThat(gradebookQueue.due(100)).noneMatch(work -> work.gradebookId().equals(gradebook));
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM tbl_gradebook_refresh WHERE gradebook_id=? AND student_id=?
                    """, Long.class, gradebook, healthy.getId())).isZero();

            exhaustRefreshRetries(new GradebookRefreshRepository.Work(classroom, gradebook, student.getId()));
        } finally {
            jdbc.execute("ALTER TABLE tbl_gradebook_history DROP CONSTRAINT test_refresh_failure");
        }
        overrideQuizGrade(teacher, classroom, quiz, student, 95);
        assertRefreshReset(new GradebookRefreshRepository.Work(classroom, gradebook, student.getId()));
        assertThat(gradebookRefresh.processNext()).isTrue();
        mvc.perform(get("/api/v1/classrooms/" + classroom + "/gradebook/me")
                .with(authentication(principal(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.finalGrade.percentage").value(95));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_gradebook_refresh WHERE gradebook_id=?",
                Long.class, gradebook)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM tbl_gradebook_history WHERE gradebook_id=? AND action='SOURCE_RECALCULATED'
                """, Long.class, gradebook)).isEqualTo(2L);

        jdbc.update("UPDATE tbl_quiz_attempt SET score=90 WHERE quiz_id=? AND student_id=?", quiz, healthy.getId());
        gradebookQueue.enqueue(new GradeSourceChanged(classroom, healthy.getId()));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> contestVersion(barrier, () -> gradebookRefresh.processNext()));
            var second = executor.submit(() -> contestVersion(barrier, () -> gradebookRefresh.processNext()));
            assertThat(List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS))).containsOnly("COMMITTED");
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM tbl_notification WHERE recipient_id=? AND type='GRADEBOOK_FINAL_CHANGED'
                """, Long.class, healthy.getId())).isEqualTo(2L);
    }

    private void exhaustRefreshRetries(GradebookRefreshRepository.Work work) {
        for (int attempt = 2; attempt <= 8; attempt++) {
            jdbc.update("""
                    UPDATE tbl_gradebook_refresh SET next_attempt_at=now()
                    WHERE gradebook_id=? AND student_id=?
                    """, work.gradebookId(), work.studentId());
            gradebookRefresh.process(work);
        }
        assertThat(jdbc.queryForObject("""
                SELECT failed_at IS NOT NULL AND next_attempt_at IS NULL AND attempt_count=8
                FROM tbl_gradebook_refresh WHERE gradebook_id=? AND student_id=?
                """, Boolean.class, work.gradebookId(), work.studentId())).isTrue();
    }

    private void assertRefreshReset(GradebookRefreshRepository.Work work) {
        assertThat(jdbc.queryForObject("""
                SELECT failed_at IS NULL AND attempt_count=0 AND next_attempt_at <= now() AND last_error IS NULL
                FROM tbl_gradebook_refresh WHERE gradebook_id=? AND student_id=?
                """, Boolean.class, work.gradebookId(), work.studentId())).isTrue();
    }

    private void overrideQuizGrade(AccountEntity teacher, UUID classroom, UUID quiz, AccountEntity student, int score)
            throws Exception {
        mvc.perform(post("/api/v1/classrooms/" + classroom + "/quizzes/" + quiz + "/students/"
                + student.getId() + "/grade-overrides").with(authentication(principal(teacher)))
                .contentType("application/json").content("""
                        {"score":%d,"reason":"Corrected quiz grade"}
                        """.formatted(score))).andExpect(status().isCreated());
    }

    @Test
    void assignmentPolicyChangeRefreshesPublishedOverallOnlyWhenPolicyChanges() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroom = classroom(teacher);
        classrooms.directAdd(teacher.getId(), classroom, new ClassroomDtos.DirectAdd(student.getUsername()));
        var assignment = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_assignment(id,classroom_id,title,status,scoring_policy,is_grade_published)
                VALUES (?,?,'Policy','PUBLISHED','HIGHEST',true)
                """, assignment, classroom);
        for (int attempt = 1; attempt <= 2; attempt++) {
            jdbc.update("""
                    INSERT INTO tbl_assignment_attempt(id,assignment_id,student_id,attempt_number,status,started_at,
                        submitted_at,final_score,revision_snapshot,snapshot_provenance)
                    VALUES (?,?,?,?,'GRADED',now(),now(),?,
                        jsonb_build_object('questions',jsonb_build_array(jsonb_build_object('points',?::integer))),'TRUSTED')
                    """, UUID.randomUUID(), assignment, student.getId(), attempt, attempt == 1 ? 6 : 8,
                    attempt == 1 ? 6 : 10);
        }
        schemeService.apply(principal(teacher), classroom, new GradebookDtos.SchemeApplyRequest(
                GradeSchemeRevisionEntity.Mode.RAW, 0L,
                List.of(new GradebookDtos.SchemeItem(SourceType.ASSIGNMENT, assignment, null, true)), null));
        publicationService.publish(principal(teacher), classroom, "policy-change",
                new GradebookDtos.GradePublicationRequest(GradebookDtos.PublicationScope.CLASS, null, 1L));
        gradebookVisibility.update(principal(teacher), classroom, new GradebookDtos.VisibilityRequest(true, 2L));
        var base = "/api/v1/classrooms/" + classroom;
        var studentAuth = authentication(principal(student));
        mvc.perform(get(base + "/gradebook/me").with(studentAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.overall.current.percentage").value(100));
        var update = put(base + "/assignments/" + assignment).with(authentication(principal(teacher)))
                .contentType("application/json").content("{\"title\":\"Policy\",\"scoringPolicy\":\"LATEST\"}");
        mvc.perform(update).andExpect(status().isOk());
        var work = gradebookQueue.due(100).stream().filter(row -> row.classroomId().equals(classroom))
                .findFirst().orElseThrow();
        gradebookRefresh.process(work);
        mvc.perform(get(base + "/gradebook/me").with(studentAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.current.earnedPoints").value(8))
                .andExpect(jsonPath("$.data.overall.current.possiblePoints").value(10))
                .andExpect(jsonPath("$.data.overall.finalGrade.percentage").value(80));
        mvc.perform(update).andExpect(status().isOk());
        assertThat(gradebookQueue.due(100)).doesNotContain(work);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"ASSIGNMENT,RAW", "ASSIGNMENT,WEIGHTED", "QUIZ,RAW", "QUIZ,WEIGHTED"})
    void rejectsUngradedItemOnApplyAndIgnoresItIfAnExistingItemChanges(
            SourceType source, GradeSchemeRevisionEntity.Mode mode) throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroom = classroom(teacher);
        classrooms.directAdd(teacher.getId(), classroom, new ClassroomDtos.DirectAdd(student.getUsername()));
        var remaining = publishedQuiz(classroom, student.getId(), 80);
        var changed = source == SourceType.QUIZ ? publishedQuiz(classroom, student.getId(), 50) : UUID.randomUUID();
        if (source == SourceType.ASSIGNMENT) {
            jdbc.update("INSERT INTO tbl_assignment(id,classroom_id,title,status) VALUES (?,?,'Changed','PUBLISHED')",
                    changed, classroom);
        }
        String toggle = source == SourceType.QUIZ ? "UPDATE tbl_quiz SET graded=? WHERE id=?"
                : "UPDATE tbl_assignment SET type=? WHERE id=?";
        Object graded = source == SourceType.QUIZ ? true : "GRADED";
        Object ungraded = source == SourceType.QUIZ ? false : "UNGRADED";
        var base = "/api/v1/classrooms/" + classroom + "/gradebook";
        var auth = authentication(principal(teacher));
        var scheme = """
                {"mode":"%s","expectedVersion":0,"items":[
                    {"sourceType":"QUIZ","sourceId":"%s","weight":40,"required":true},
                    {"sourceType":"%s","sourceId":"%s","weight":60,"required":true}]}
                """.formatted(mode, remaining, source, changed);
        jdbc.update(toggle, ungraded, changed);
        mvc.perform(put(base + "/scheme").with(auth).contentType("application/json").content(scheme))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("GRADEBOOK_ITEM_UNGRADED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(source + " " + changed)));
        jdbc.update(toggle, graded, changed);
        mvc.perform(put(base + "/scheme").with(auth).contentType("application/json").content(scheme))
                .andExpect(status().isOk());
        jdbc.update(toggle, ungraded, changed);
        mvc.perform(get(base + "/students/" + student.getId()).with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.current.percentage").value(80))
                .andExpect(jsonPath("$.data.overall.finalGrade.percentage").value(80));
        publicationService.publish(principal(teacher), classroom, "ungraded-existing",
                new GradebookDtos.GradePublicationRequest(GradebookDtos.PublicationScope.CLASS, null, 1L));
        gradebookVisibility.update(principal(teacher), classroom, new GradebookDtos.VisibilityRequest(true, 2L));
        mvc.perform(get(base + "/me").with(authentication(principal(student))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.overall.finalGrade.percentage").value(80));
    }

    @Test
    void hiddenOverallNotifiesOnlyStudentWithChangedPublishedFinalWithoutRevealingScores() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var changed = account(AccountRole.STUDENT);
        var unchanged = account(AccountRole.STUDENT);
        var unpublished = account(AccountRole.STUDENT);
        var classroom = classroom(teacher);
        for (var student : List.of(changed, unchanged, unpublished)) {
            classrooms.directAdd(teacher.getId(), classroom, new ClassroomDtos.DirectAdd(student.getUsername()));
        }
        var first = publishedQuiz(classroom, changed.getId(), 100);
        var second = publishedQuiz(classroom, changed.getId(), 50);
        for (var student : List.of(unchanged, unpublished)) {
            for (var quiz : List.of(first, second)) {
                jdbc.update("""
                        INSERT INTO tbl_quiz_attempt(id,quiz_id,student_id,status,started_at,submitted_at,score,grade_published,snapshot_json)
                        VALUES (?,?,?,'SUBMITTED',now(),now(),?,true,'{}'::jsonb)
                        """, UUID.randomUUID(), quiz, student.getId(),
                        student == unchanged ? 75 : quiz.equals(first) ? 100 : 50);
            }
        }
        var auth = principal(teacher);
        var mode = GradeSchemeRevisionEntity.Mode.WEIGHTED;
        var scope = GradebookDtos.PublicationScope.STUDENT;
        schemeService.apply(auth, classroom, new GradebookDtos.SchemeApplyRequest(mode, 0L, List.of(
                new GradebookDtos.SchemeItem(SourceType.QUIZ, first, new BigDecimal("20"), true),
                new GradebookDtos.SchemeItem(SourceType.QUIZ, second, new BigDecimal("80"), true)), null));
        publicationService.publish(auth, classroom, "final-changed", new GradebookDtos.GradePublicationRequest(scope, changed.getId(), 1L));
        publicationService.publish(auth, classroom, "final-same", new GradebookDtos.GradePublicationRequest(scope, unchanged.getId(), 2L));
        schemeService.apply(auth, classroom, new GradebookDtos.SchemeApplyRequest(mode, 3L, List.of(
                new GradebookDtos.SchemeItem(SourceType.QUIZ, first, new BigDecimal("80"), true),
                new GradebookDtos.SchemeItem(SourceType.QUIZ, second, new BigDecimal("20"), true)), "New weights"));
        var link = "/classrooms/" + classroom + "/gradebook";
        var studentAuth = authentication(principal(changed));
        mvc.perform(get("/api/v1/notifications").with(studentAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("GRADEBOOK_FINAL_CHANGED"))
                .andExpect(jsonPath("$.data.items[0].title").value("Overall grade updated"))
                .andExpect(jsonPath("$.data.items[0].message").value("Your overall grade was updated."))
                .andExpect(jsonPath("$.data.items[0].actionUrl").value(link))
                .andExpect(jsonPath("$.data.items[0].score").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].metadata").doesNotExist());
        mvc.perform(get("/api/v1" + link + "/me").with(studentAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall").value(org.hamcrest.Matchers.nullValue()));
        assertThat(jdbc.queryForList("""
                SELECT recipient_id FROM tbl_notification WHERE gradebook_classroom_id=? AND type='GRADEBOOK_FINAL_CHANGED'
                """, UUID.class, classroom)).containsExactly(changed.getId());
    }

    @Test
    void rejectsGradebookMutationsFromNonOwnerAndStudents() throws Exception {
        var owner = account(AccountRole.TEACHER);
        var outsider = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var peer = account(AccountRole.STUDENT);
        var classroom = classroom(owner);
        for (var member : List.of(student, peer)) {
            classrooms.directAdd(owner.getId(), classroom, new ClassroomDtos.DirectAdd(member.getUsername()));
        }
        var base = "/api/v1/classrooms/" + classroom + "/gradebook";
        var scheme = "{\"mode\":\"RAW\",\"expectedVersion\":0,\"items\":[]}";
        for (var actor : List.of(outsider, student)) {
            var auth = authentication(principal(actor));
            mvc.perform(put(base + "/scheme").with(auth).contentType("application/json").content(scheme))
                    .andExpect(status().isForbidden());
            mvc.perform(put(base + "/visibility").with(auth).contentType("application/json")
                    .content("{\"showOverall\":true,\"expectedVersion\":0}")).andExpect(status().isForbidden());
            mvc.perform(post(base + "/publications").with(auth).header("Idempotency-Key", "denied-class")
                    .contentType("application/json").content("{\"scope\":\"CLASS\",\"expectedVersion\":0}"))
                    .andExpect(status().isForbidden());
        }
        for (var target : List.of(student, peer)) {
            mvc.perform(post(base + "/publications").with(authentication(principal(student)))
                    .header("Idempotency-Key", "denied-student")
                    .contentType("application/json").content("""
                            {"scope":"STUDENT","studentId":"%s","expectedVersion":0}
                            """.formatted(target.getId()))).andExpect(status().isForbidden());
        }
        assertThat(gradebook(classroom).getVersion()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_gradebook_publication WHERE gradebook_id=?",
                Long.class, gradebook(classroom).getId())).isZero();
    }

    @Test
    void classPublicationUsesRosterBeforeOrAfterConcurrentRemoval() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var kept = account(AccountRole.STUDENT);
        var removed = account(AccountRole.STUDENT);
        var classroom = classroom(teacher);
        for (var member : List.of(kept, removed)) {
            classrooms.directAdd(teacher.getId(), classroom, new ClassroomDtos.DirectAdd(member.getUsername()));
        }
        var quiz = publishedQuiz(classroom, kept.getId(), 75);
        jdbc.update("""
                INSERT INTO tbl_quiz_attempt(id,quiz_id,student_id,status,started_at,submitted_at,score,grade_published,snapshot_json)
                VALUES (?,?,?,'SUBMITTED',now(),now(),80,true,'{}'::jsonb)
                """, UUID.randomUUID(), quiz, removed.getId());
        var auth = principal(teacher);
        schemeService.apply(auth, classroom, new GradebookDtos.SchemeApplyRequest(GradeSchemeRevisionEntity.Mode.RAW, 0L,
                List.of(new GradebookDtos.SchemeItem(SourceType.QUIZ, quiz, null, true)), null));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        GradebookDtos.GradePublicationResponse receipt;
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var publish = executor.submit(() -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return publicationService.publish(auth, classroom, "roster-race",
                        new GradebookDtos.GradePublicationRequest(GradebookDtos.PublicationScope.CLASS, null, 1L)).response();
            });
            var removal = executor.submit(() -> contestVersion(barrier,
                    () -> classrooms.removeStudent(teacher.getId(), classroom, removed.getId())));
            receipt = publish.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(removal.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("COMMITTED");
        }
        var recipients = jdbc.queryForList("SELECT student_id FROM tbl_gradebook_student_result WHERE publication_id=?",
                UUID.class, receipt.publicationId());
        assertThat(receipt.publishedCount()).isBetween(1, 2);
        assertThat(receipt.ineligibleCount()).isZero();
        assertThat(recipients).hasSize(receipt.publishedCount()).contains(kept.getId()).doesNotHaveDuplicates();
        if (receipt.publishedCount() == 2) assertThat(recipients).contains(removed.getId());
        mvc.perform(get("/api/v1/classrooms/" + classroom + "/gradebook/me")
                .with(authentication(principal(removed)))).andExpect(status().isForbidden());
    }

    @Test
    void csvExportAtConfiguredLimitIncludesTheSecondPageAndRejectsOneAdditionalRow() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var classroom = classroom(teacher);
        var members = jdbc.queryForList("""
                INSERT INTO tbl_account(id,username,email,role,status,password_hash)
                SELECT id,'exp_'||replace(id::text,'-',''),id||'@example.test','STUDENT','ACTIVE','fixture'
                FROM (SELECT gen_random_uuid() AS id FROM generate_series(1,101)) seed
                RETURNING id
                """, UUID.class);
        jdbc.batchUpdate("""
                INSERT INTO tbl_classroom_membership(id,classroom_id,student_id,status,joined_at)
                VALUES (gen_random_uuid(),?,?,'ACTIVE',now())
                """, members.stream().map(id -> new Object[]{classroom, id}).toList());
        var base = "/api/v1/classrooms/" + classroom + "/gradebook";
        var auth = authentication(principal(teacher));
        var response = mvc.perform(get(base + "/export").param("format", "csv").with(auth))
                .andExpect(status().isOk()).andReturn().getResponse();
        try (var parser = org.apache.commons.csv.CSVParser.parse(
                response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8), org.apache.commons.csv.CSVFormat.RFC4180)) {
            var rows = parser.getRecords();
            assertThat(rows).hasSize(102);
            assertThat(rows.subList(1, rows.size())).extracting(row -> UUID.fromString(row.get(0)))
                    .containsExactlyInAnyOrderElementsOf(members);
        }
        mvc.perform(get(base + "/summary").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students").value(101));
        var extra = account(AccountRole.STUDENT);
        classrooms.directAdd(teacher.getId(), classroom, new ClassroomDtos.DirectAdd(extra.getUsername()));
        mvc.perform(get(base + "/export").param("format", "csv").with(auth))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("GRADEBOOK_EXPORT_LIMIT_EXCEEDED"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test
    void gradebookDoesNotReadPublicPracticeOrCourseHistory() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var publicAuthor = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroom = classroom(teacher);
        classrooms.directAdd(teacher.getId(), classroom, new ClassroomDtos.DirectAdd(student.getUsername()));
        var quiz = publishedQuiz(classroom, student.getId(), 80);
        var problem = UUID.randomUUID();
        var course = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_public_problem(id,author_id,status,review_status) VALUES (?,?,'DRAFT','PENDING_REVIEW')",
                problem, publicAuthor.getId());
        jdbc.update("""
                INSERT INTO tbl_submission(id,owner_id,scope,resource_id,revision_id,language,source,testcase_snapshot)
                VALUES (?,?,'PUBLIC_PROBLEM',?,?,'PYTHON','private-source-fixture','[]')
                """, UUID.randomUUID(), student.getId(), problem, UUID.randomUUID());
        jdbc.update("INSERT INTO tbl_public_course(id,author_id,title,status) VALUES (?,?,'Private course fixture','DRAFT')",
                course, publicAuthor.getId());
        jdbc.update("INSERT INTO tbl_course_enrollment(id,course_id,account_id) VALUES (?,?,?)",
                UUID.randomUUID(), course, student.getId());
        var base = "/api/v1/classrooms/" + classroom + "/gradebook";
        var auth = authentication(principal(teacher));
        mvc.perform(get(base).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].grades.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].grades[0].sourceId").value(quiz.toString()))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-source-fixture"))));
        for (var id : List.of(problem, course)) {
            for (var source : SourceType.values()) {
                mvc.perform(get(base + "/items/{id}/submissions", id).param("sourceType", source.name()).with(auth))
                        .andExpect(status().isForbidden());
            }
        }
    }

    private UUID publishedQuiz(UUID classroomId, UUID student, int score) {
        var item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_quiz(id,classroom_id,title,status,graded,max_attempts,questions_json)
                VALUES (?,?,'Quiz','PUBLISHED',true,1,'[]'::jsonb)
                """, item, classroomId);
        jdbc.update("""
                INSERT INTO tbl_quiz_attempt(id,quiz_id,student_id,status,started_at,submitted_at,score,grade_published,snapshot_json)
                VALUES (?,?,?,'SUBMITTED',now(),now(),?,true,'{}'::jsonb)
                """, UUID.randomUUID(), item, student, score);
        return item;
    }

    @Test
    void publishesCurrentWithHiddenFinalAndReplaysSameRequestWithoutChangingVersion() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroomId = classroom(teacher);
        classrooms.directAdd(teacher.getId(), classroomId, new ClassroomDtos.DirectAdd(student.getUsername()));
        var publishedQuiz = UUID.randomUUID();
        var hiddenQuiz = UUID.randomUUID();
        for (var item : List.of(publishedQuiz, hiddenQuiz)) {
            jdbc.update("""
                    INSERT INTO tbl_quiz(id,classroom_id,title,status,graded,max_attempts,questions_json)
                    VALUES (?,?,'Quiz','PUBLISHED',true,1,'[]'::jsonb)
                    """, item, classroomId);
            jdbc.update("""
                    INSERT INTO tbl_quiz_attempt(id,quiz_id,student_id,status,started_at,submitted_at,score,grade_published,snapshot_json)
                    VALUES (?,?,?,'SUBMITTED',now(),now(),80,?,'{}'::jsonb)
                    """, UUID.randomUUID(), item, student.getId(), item.equals(publishedQuiz));
        }
        schemeService.apply(principal(teacher), classroomId, new GradebookDtos.SchemeApplyRequest(
                GradeSchemeRevisionEntity.Mode.RAW, 0L,
                List.of(new GradebookDtos.SchemeItem(SourceType.QUIZ, publishedQuiz, null, true),
                        new GradebookDtos.SchemeItem(SourceType.QUIZ, hiddenQuiz, null, true)), null));
        var base = "/api/v1/classrooms/" + classroomId + "/gradebook";
        var teacherAuth = authentication(principal(teacher));
        var studentAuth = authentication(principal(student));
        var request = """
                {"scope":"STUDENT","studentId":"%s","expectedVersion":1}
                """.formatted(student.getId());
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            publicationService.publish(principal(teacher), classroomId, "publish-rollback",
                    new GradebookDtos.GradePublicationRequest(GradebookDtos.PublicationScope.STUDENT, student.getId(), 1L));
            throw new IllegalStateException("force publication rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("force publication rollback");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM tbl_audit_log WHERE classroom_id=? AND action='GRADEBOOK_PUBLISHED'
                """, Long.class, classroomId)).isZero();
        var first = mvc.perform(post(base + "/publications").header("Idempotency-Key", "test-key").header("User-Agent", "Gradebook publish test")
                .contentType("application/json").content(request).with(teacherAuth))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(2)).andReturn();
        var replay = mvc.perform(post(base + "/publications").header("Idempotency-Key", "test-key")
                .contentType("application/json").content(request).with(teacherAuth))
                .andExpect(status().isOk()).andReturn();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(json.readTree(first.getResponse().getContentAsString()).get("data"))
                .isEqualTo(json.readTree(replay.getResponse().getContentAsString()).get("data"));
        var actualPublicationId = json.readTree(first.getResponse().getContentAsString()).at("/data/publicationId").asText();
        var audit = jdbc.queryForList("""
                SELECT actor_id,client_ip,user_agent,metadata->>'publicationId' AS publication_id,
                    metadata->>'scope' AS scope,metadata->>'studentId' AS student_id
                FROM tbl_audit_log WHERE classroom_id=? AND action='GRADEBOOK_PUBLISHED'
                """, classroomId);
        assertThat(audit).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("actor_id", teacher.getId())
                .containsEntry("user_agent", "Gradebook publish test")
                .containsEntry("scope", "STUDENT")
                .containsEntry("student_id", student.getId().toString())
                .containsEntry("publication_id", actualPublicationId)
                .hasEntrySatisfying("client_ip", ip -> assertThat(ip).isNotNull()));
        mvc.perform(post(base + "/publications").header("Idempotency-Key", "test-key").with(teacherAuth)
                .contentType("application/json").content("{\"scope\":\"CLASS\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GRADEBOOK_IDEMPOTENCY_CONFLICT"));
        mvc.perform(post(base + "/publications").header("Idempotency-Key", "stale-key")
                .contentType("application/json").content(request).with(teacherAuth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("GRADEBOOK_VERSION_CONFLICT"));
        mvc.perform(get(base + "/me").with(studentAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall").doesNotExist());
        mvc.perform(put(base + "/visibility").contentType("application/json")
                .content("""
                        {"showOverall":true,"expectedVersion":2}
                        """).with(teacherAuth)).andExpect(status().isOk());
        mvc.perform(get(base + "/me").with(studentAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.current.percentage").value(80))
                .andExpect(jsonPath("$.data.overall.finalGrade").doesNotExist())
                .andExpect(jsonPath("$.data.items.length()").value(1));
        quizService.publishGrades(teacher.getId(), classroomId, hiddenQuiz);
        var work = gradebookQueue.due(100).stream().filter(candidate -> candidate.classroomId().equals(classroomId))
                .findFirst().orElseThrow();
        var processors = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var firstWorker = executor.submit(() -> contestVersion(processors, () -> gradebookRefresh.process(work)));
            var secondWorker = executor.submit(() -> contestVersion(processors, () -> gradebookRefresh.process(work)));
            assertThat(List.of(firstWorker.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    secondWorker.get(20, java.util.concurrent.TimeUnit.SECONDS))).containsOnly("COMMITTED");
        }
        gradebookRefresh.process(work);
        assertThat(gradebookQueue.due(100)).doesNotContain(work);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_gradebook_student_result WHERE gradebook_id=?",
                Long.class, gradebook(classroomId).getId())).isEqualTo(2L);
        mvc.perform(get(base + "/me").with(studentAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.finalGrade.percentage").value(80))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void studentDtoIsIdenticalAfterChangingHiddenGradeAndTeacherRoutesRejectStudent() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        var student = account(AccountRole.STUDENT);
        var classroomId = classroom(teacher);
        classrooms.directAdd(teacher.getId(), classroomId, new ClassroomDtos.DirectAdd(student.getUsername()));
        var item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_quiz(id,classroom_id,title,status,graded,max_attempts,questions_json)
                VALUES (?,?,'Quiz','PUBLISHED',true,2,'[]'::jsonb)
                """, item, classroomId);
        var visible = UUID.randomUUID();
        var hidden = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_quiz_attempt(id,quiz_id,student_id,status,started_at,submitted_at,score,grade_published,snapshot_json)
                VALUES (?,?,?,'SUBMITTED',now(),now(),0,true,'{}'::jsonb),
                       (?,?,?,'SUBMITTED',now(),now(),80,false,'{}'::jsonb)
                """, visible, item, student.getId(), hidden, item, student.getId());
        var base = "/api/v1/classrooms/" + classroomId + "/gradebook";
        var auth = authentication(principal(student));
        var before = mvc.perform(get(base + "/me").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].earnedPoints").value(0))
                .andExpect(jsonPath("$.data.overall").doesNotExist()).andReturn();
        jdbc.update("UPDATE tbl_quiz_attempt SET score=95 WHERE id=?", hidden);
        var after = mvc.perform(get(base + "/me").with(auth)).andExpect(status().isOk()).andReturn();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(json.readTree(after.getResponse().getContentAsString()).get("data"))
                .isEqualTo(json.readTree(before.getResponse().getContentAsString()).get("data"));
        mvc.perform(get(base).with(auth)).andExpect(status().isForbidden());
        mvc.perform(get(base).with(authentication(principal(teacher)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].grades[0].earnedPoints").value(95));
        mvc.perform(get(base + "/items/{id}/submissions", item).param("sourceType", "QUIZ")
                .with(authentication(principal(teacher)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2));
    }

    @Test
    void appliesImmutableSchemeAndPreservesItWhenUpdateIsRejected() {
        var teacher = account(AccountRole.TEACHER);
        var classroomId = classroom(teacher);
        var item = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_assignment(id,classroom_id,title) VALUES (?,?,'Assignment')", item, classroomId);
        var principal = principal(teacher);
        var weighted = GradeSchemeRevisionEntity.Mode.WEIGHTED;
        var schemeItem = new GradebookDtos.SchemeItem(SourceType.ASSIGNMENT, item, new BigDecimal("100"), true);
        var applied = schemeService.apply(principal, classroomId,
                new GradebookDtos.SchemeApplyRequest(weighted, 0L, List.of(schemeItem), "Initial weights"));
        assertThat(applied.version()).isEqualTo(1);
        assertThat(applied.revisionNumber()).isEqualTo(1);
        assertThat(gradebook(classroomId).getActiveSchemeRevisionId()).isEqualTo(applied.revisionId());
        var badItem = new GradebookDtos.SchemeItem(SourceType.ASSIGNMENT, item, new BigDecimal("99.9999"), true);
        assertThatThrownBy(() -> schemeService.apply(principal, classroomId,
                new GradebookDtos.SchemeApplyRequest(weighted, 1L, List.of(badItem), null))).isInstanceOf(AppException.class);
        assertThat(schemeService.get(principal, classroomId).revisionId()).isEqualTo(applied.revisionId());
        var raw = schemeService.apply(principal, classroomId,
                new GradebookDtos.SchemeApplyRequest(GradeSchemeRevisionEntity.Mode.RAW, 1L, List.of(schemeItem), "Raw totals"));
        assertThat(raw.version()).isEqualTo(2);
        assertThat(raw.revisionNumber()).isEqualTo(2);
        assertThat(items.findAllByRevisionId(applied.revisionId())).singleElement()
                .satisfies(saved -> assertThat(saved.getWeight()).isEqualByComparingTo("100"));
    }

    @Test
    void createsGradebookWithDefaultsAndNoRevision() {
        UUID classroomId = classroom(account(AccountRole.TEACHER));
        var gradebook = gradebook(classroomId);
        assertThat(gradebook.isShowOverall()).isFalse();
        assertThat(gradebook.getActiveSchemeRevisionId()).isNull();
        assertThat(gradebook.getVersion()).isZero();
        assertThat(revisions.existsForClassroom(classroomId)).isFalse();
    }

    @Test
    void rollsBackClassroomAndGradebookAfterFlushingBoth() {
        var teacher = account(AccountRole.TEACHER);
        var createdId = new AtomicReference<UUID>();
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            createdId.set(classroom(teacher));
            gradebooks.flush();
            assertThat(gradebook(createdId.get()).getId()).isNotNull();
            throw new IllegalStateException("Abort classroom creation");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Abort classroom creation");
        assertThat(createdId.get()).isNotNull();
        assertThat(gradebooks.findByClassroomId(createdId.get())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM tbl_classroom WHERE id=?)",
                Boolean.class, createdId.get())).isFalse();
    }

    @Test
    void hardDeleteWithUnappliedRevisionReturnsConflict() throws Exception {
        var teacher = account(AccountRole.TEACHER);
        UUID classroomId = classroom(teacher);
        var gradebook = gradebook(classroomId);
        saveRevision(teacher, gradebook, GradeSchemeRevisionEntity.Mode.RAW);

        mvc.perform(delete("/api/v1/classrooms/{id}", classroomId).with(authentication(principal(teacher))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLASS_HAS_RETAINED_DATA"))
                .andExpect(jsonPath("$.message").value("Archive this classroom to preserve its gradebook revisions"));
        assertThat(gradebook(classroomId).getActiveSchemeRevisionId()).isNull();
        assertThat(revisions.existsForClassroom(classroomId)).isTrue();
    }

    @Test
    void rejectsForeignClassItemAndStudentAndRevokesOldOwnerAfterTransfer() {
        var teacher = account(AccountRole.TEACHER);
        var otherTeacher = account(AccountRole.TEACHER);
        var admin = account(AccountRole.ADMIN);
        var student = account(AccountRole.STUDENT);
        var otherStudent = account(AccountRole.STUDENT);
        UUID first = classroom(teacher);
        UUID second = classroom(otherTeacher);
        classrooms.directAdd(teacher.getId(), first, new ClassroomDtos.DirectAdd(student.getUsername()));
        classrooms.directAdd(otherTeacher.getId(), second, new ClassroomDtos.DirectAdd(otherStudent.getUsername()));
        UUID assignment = UUID.randomUUID();
        UUID quiz = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_assignment(id,classroom_id,title) VALUES (?,?,'Assignment')", assignment, first);
        jdbc.update("""
                INSERT INTO tbl_quiz(id,classroom_id,title,graded,max_attempts,questions_json)
                VALUES (?,?,'Quiz',true,1,'[]'::jsonb)
                """, quiz, second);

        var teacherPrincipal = principal(teacher);
        var studentPrincipal = principal(student);
        assertThat(access.requireTeacherRead(teacherPrincipal, first)).isEqualTo(teacher.getId());
        assertThatThrownBy(() -> access.requireTeacherRead(teacherPrincipal, second)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> access.requireTeacherWrite(teacherPrincipal, first))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(access.requireStudentRead(studentPrincipal, first, student.getId())).isEqualTo(student.getId());
        assertThatThrownBy(() -> access.requireStudentRead(studentPrincipal, second, student.getId()))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> access.requireStudentRead(studentPrincipal, first, otherStudent.getId()))
                .isInstanceOf(AppException.class);

        transactions.executeWithoutResult(status -> {
            access.requireTeacherWrite(teacherPrincipal, first);
            access.requireActiveStudentInClass(first, student.getId());
            access.requireItemInClass(first, SourceType.ASSIGNMENT, assignment);
        });
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            access.requireTeacherWrite(teacherPrincipal, first);
            access.requireActiveStudentInClass(first, otherStudent.getId());
        })).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            access.requireTeacherWrite(teacherPrincipal, first);
            access.requireItemInClass(first, SourceType.QUIZ, quiz);
        })).isInstanceOf(AppException.class);
        transactions.executeWithoutResult(status -> {
            access.requireTeacherWrite(principal(otherTeacher), second);
            access.requireItemInClass(second, SourceType.QUIZ, quiz);
        });

        classrooms.leave(student.getId(), first);
        assertThatThrownBy(() -> access.requireStudentRead(studentPrincipal, first, student.getId()))
                .isInstanceOf(AppException.class);
        classrooms.transferOwner(admin.getId(), first, otherTeacher.getId());
        assertThatThrownBy(() -> access.requireTeacherRead(teacherPrincipal, first)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> access.requireTeacherWrite(teacherPrincipal, first)))
                .isInstanceOf(AppException.class);
        transactions.executeWithoutResult(status -> access.requireTeacherWrite(principal(otherTeacher), first));
        classrooms.archive(otherTeacher.getId(), first);
        assertThat(access.requireTeacherRead(principal(otherTeacher), first)).isEqualTo(otherTeacher.getId());
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> access.requireTeacherWrite(principal(otherTeacher), first)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void persistsScopedSchemeAndRejectsDuplicateItemAndForeignActiveRevision() {
        var teacher = account(AccountRole.TEACHER);
        var gradebook = gradebook(classroom(teacher));
        var otherGradebook = gradebook(classroom(teacher));
        UUID sourceId = UUID.randomUUID();
        jdbc.update("INSERT INTO tbl_assignment(id,classroom_id,title) VALUES (?,?,'Assignment')",
                sourceId, gradebook.getClassroomId());

        var revision = saveRevision(teacher, gradebook, GradeSchemeRevisionEntity.Mode.WEIGHTED);
        items.saveAndFlush(item(revision.getId(), sourceId));
        gradebook.setActiveSchemeRevisionId(revision.getId());
        gradebooks.saveAndFlush(gradebook);

        assertThat(gradebooks.findByClassroomId(gradebook.getClassroomId()).orElseThrow().getActiveSchemeRevisionId())
                .isEqualTo(revision.getId());
        assertThat(revisions.findByIdAndGradebookId(revision.getId(), otherGradebook.getId())).isEmpty();
        assertThat(items.findAllByRevisionId(revision.getId())).singleElement()
                .satisfies(item -> assertThat(item.getWeight()).isEqualByComparingTo("100.0000"));
        assertThatThrownBy(() -> items.saveAndFlush(item(revision.getId(), sourceId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tbl_grade_scheme_revision SET mode='RAW' WHERE id=?", revision.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tbl_grade_scheme_item WHERE revision_id=?", revision.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        otherGradebook.setActiveSchemeRevisionId(revision.getId());
        assertThatThrownBy(() -> gradebooks.saveAndFlush(otherGradebook))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emptyGradebookDoesNotPreventExistingClassroomDeletion() {
        var teacher = account(AccountRole.TEACHER);
        UUID classroomId = classroom(teacher);
        assertThat(gradebook(classroomId).getId()).isNotNull();
        classrooms.delete(teacher.getId(), classroomId);
        assertThat(gradebooks.findByClassroomId(classroomId)).isEmpty();
    }

    private GradebookEntity gradebook(UUID classroomId) {
        return gradebooks.findByClassroomId(classroomId).orElseThrow();
    }

    private GradeSchemeRevisionEntity saveRevision(AccountEntity teacher, GradebookEntity gradebook,
                                                    GradeSchemeRevisionEntity.Mode mode) {
        var revision = new GradeSchemeRevisionEntity();
        revision.setId(UUID.randomUUID());
        revision.setGradebookId(gradebook.getId());
        revision.setRevisionNo(1);
        revision.setMode(mode);
        revision.setCreatedBy(teacher.getId());
        return revisions.saveAndFlush(revision);
    }

    private static GradeSchemeItemEntity item(UUID revisionId, UUID sourceId) {
        var item = new GradeSchemeItemEntity();
        item.setId(UUID.randomUUID());
        item.setRevisionId(revisionId);
        item.setSourceType(SourceType.ASSIGNMENT);
        item.setSourceId(sourceId);
        item.setWeight(new BigDecimal("100.0000"));
        return item;
    }

    private UUID classroom(AccountEntity teacher) {
        return classrooms.create(teacher.getId(), new ClassroomDtos.Create("Gradebook", null, JoinMode.AUTO_JOIN, null, null)).id();
    }

    private static UsernamePasswordAuthenticationToken principal(AccountEntity account) {
        return new UsernamePasswordAuthenticationToken(account.getId().toString(), "",
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("grd_" + account.getId().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test");
        account.setPasswordHash("unused");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }
}
