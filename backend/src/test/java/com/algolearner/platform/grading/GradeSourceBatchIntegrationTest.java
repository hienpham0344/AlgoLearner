package com.algolearner.platform.grading;

import static com.algolearner.platform.grading.GradeSourceResult.Audience.STUDENT;
import static com.algolearner.platform.grading.GradeSourceResult.Audience.TEACHER;
import static com.algolearner.platform.grading.GradeSourceResult.Status.GRADED;
import static com.algolearner.platform.grading.GradeSourceResult.Status.NOT_SUBMITTED;
import static com.algolearner.platform.grading.GradeSourceResult.Status.UNGRADED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.algolearner.platform.assignment.AssignmentGradeSource;
import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.ClassroomDtos;
import com.algolearner.platform.classroom.ClassroomService;
import com.algolearner.platform.classroom.JoinMode;
import com.algolearner.platform.quiz.QuizGradeSource;
import com.algolearner.platform.quiz.QuizService;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=gradebook-test-only-secret-at-least-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class GradeSourceBatchIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired ClassroomService classrooms;
    @Autowired QuizService quizService;

    @Test
    void bothAudiencesUseOneQueryPerSourceForOneAndFortyCells() {
        var teacher = account(AccountRole.TEACHER);
        var classId = classroom(teacher);
        var students = IntStream.range(0, 5).mapToObj(i -> member(teacher, classId)).toList();
        var assignments = IntStream.range(0, 8).mapToObj(i -> assignment(classId)).toList();
        var quizzes = IntStream.range(0, 8).mapToObj(i -> quiz(classId)).toList();
        for (var student : students) {
            for (var item : assignments) legacyAttempt(item, student, 1, 5);
            for (var item : quizzes) quizAttempt(item, student, 50, true);
        }
        var template = spy(new NamedParameterJdbcTemplate(jdbc));
        var assignmentSource = new AssignmentGradeSource(template);
        var quizSource = new QuizGradeSource(template);
        for (var audience : GradeSourceResult.Audience.values()) {
            assertThat(assignmentSource.load(classId, assignments.subList(0, 1), students.subList(0, 1), audience)).hasSize(1);
            verifyQueries(template, 1);
            clearInvocations(template);
            assertThat(assignmentSource.load(classId, assignments, students, audience)).hasSize(40);
            verifyQueries(template, 1);
            clearInvocations(template);
            assertThat(quizSource.load(classId, quizzes.subList(0, 1), students.subList(0, 1), audience)).hasSize(1);
            verifyQueries(template, 1);
            clearInvocations(template);
            assertThat(quizSource.load(classId, quizzes, students, audience)).hasSize(40);
            verifyQueries(template, 1);
            clearInvocations(template);
        }
    }

    @Test
    void highestAndLatestChooseDifferentAttemptsAndKeepTheirOwnSnapshotAndOverride() {
        var teacher = account(AccountRole.TEACHER);
        var classId = classroom(teacher);
        var student = member(teacher, classId);
        var item = assignment(classId);
        var older = evaluatedAttempt(item, student, 1, 6, 6, false, true);
        var latest = evaluatedAttempt(item, student, 2, 8, 10, false, true);
        var source = new AssignmentGradeSource(new NamedParameterJdbcTemplate(jdbc));
        for (var audience : GradeSourceResult.Audience.values()) {
            assertThat(source.load(classId, List.of(item), List.of(student), audience))
                    .singleElement().satisfies(result -> {
                        assertThat(result.provenance().attemptId()).isEqualTo(older);
                        assertThat(result.earnedPoints()).isEqualByComparingTo("6");
                        assertThat(result.possiblePoints()).isEqualByComparingTo("6");
                    });
        }
        jdbc.update("UPDATE tbl_assignment SET scoring_policy='LATEST' WHERE id=?", item);
        for (var audience : GradeSourceResult.Audience.values()) {
            assertThat(source.load(classId, List.of(item), List.of(student), audience))
                    .singleElement().satisfies(result -> {
                        assertThat(result.provenance().attemptId()).isEqualTo(latest);
                        assertThat(result.earnedPoints()).isEqualByComparingTo("8");
                        assertThat(result.possiblePoints()).isEqualByComparingTo("10");
                    });
        }
    }

    @Test
    void batchesBothSourcesAndStudentRowsAreStableWhenHiddenGradesChange() {
        var teacher = account(AccountRole.TEACHER);
        var classId = classroom(teacher);
        var otherTeacher = account(AccountRole.TEACHER);
        var otherClass = classroom(otherTeacher);
        var student = member(teacher, classId);
        var otherStudent = member(teacher, classId);
        var foreignStudent = member(otherTeacher, otherClass);
        var assignment = assignment(classId);
        var foreignAssignment = assignment(otherClass);
        var quiz = quiz(classId);
        var foreignQuiz = quiz(otherClass);
        var legacy = legacyAttempt(assignment, student, 1, 0);
        var quizAttempt = quizAttempt(quiz, student, 35, true);
        var hiddenQuizAttempt = quizAttempt(quiz, student, 80, false);
        jdbc.update("""
                INSERT INTO tbl_quiz_grade_override(quiz_id,student_id,attempt_id,actor_id,original_score,final_score,reason)
                VALUES (?,?,?,?,35,40,'Published attempt override')
                """, quiz, student, quizAttempt, teacher.getId());

        var template = spy(new NamedParameterJdbcTemplate(jdbc));
        var assignments = new AssignmentGradeSource(template);
        var quizzes = new QuizGradeSource(template);
        var publishedAssignment = assignments.load(classId, List.of(assignment), List.of(student), STUDENT);
        var publishedQuiz = quizzes.load(classId, List.of(quiz), List.of(student), STUDENT);
        assertThat(publishedAssignment).singleElement().satisfies(result -> {
            assertThat(result.earnedPoints()).isZero();
            assertThat(result.possiblePoints()).isNull();
            assertThat(result.status()).isEqualTo(GRADED);
            assertThat(result.provenance().attemptId()).isEqualTo(legacy);
        });
        assertThat(publishedQuiz).singleElement().satisfies(result -> {
            assertThat(result.earnedPoints()).isEqualByComparingTo("40");
            assertThat(result.possiblePoints()).isEqualByComparingTo("100");
            assertThat(result.provenance().attemptId()).isEqualTo(quizAttempt);
            assertThat(result.provenance().quizOverrideId()).isNotNull();
        });
        verifyQueries(template, 2);

        hiddenAssignmentEvaluation(assignment, student);
        jdbc.update("UPDATE tbl_quiz_attempt SET score=95 WHERE id=?", hiddenQuizAttempt);
        jdbc.update("""
                INSERT INTO tbl_quiz_grade_override(quiz_id,student_id,attempt_id,actor_id,original_score,final_score,reason)
                VALUES (?,?,?,?,95,97,'Teacher override')
                """, quiz, student, hiddenQuizAttempt, teacher.getId());
        assertThat(assignments.load(classId, List.of(assignment), List.of(student), STUDENT)).isEqualTo(publishedAssignment);
        assertThat(quizzes.load(classId, List.of(quiz), List.of(student), STUDENT)).isEqualTo(publishedQuiz);

        clearInvocations(template);
        assertThat(assignments.load(classId, List.of(assignment), List.of(student), TEACHER)).hasSize(1);
        assertThat(quizzes.load(classId, List.of(quiz), List.of(student), TEACHER)).hasSize(1);
        verifyQueries(template, 2);

        clearInvocations(template);
        var students = List.of(student, otherStudent, foreignStudent);
        var assignmentRows = assignments.load(classId, List.of(assignment, assignment(classId), foreignAssignment), students, TEACHER);
        var quizRows = quizzes.load(classId, List.of(quiz, quiz(classId), foreignQuiz), students, TEACHER);
        assertThat(assignmentRows).hasSize(4);
        assertThat(quizRows).hasSize(4);
        assertThat(assignmentRows).noneMatch(row -> row.sourceId().equals(foreignAssignment)
                || row.studentId().equals(foreignStudent));
        assertThat(quizRows).noneMatch(row -> row.sourceId().equals(foreignQuiz)
                || row.studentId().equals(foreignStudent));
        assertThat(assignmentRows).filteredOn(row -> row.sourceId().equals(assignment) && row.studentId().equals(student))
                .singleElement().satisfies(result -> {
                    assertThat(result.earnedPoints()).isEqualByComparingTo("7");
                    assertThat(result.possiblePoints()).isEqualByComparingTo("10");
                    assertThat(result.published()).isFalse();
                    assertThat(result.progress()).isEqualTo(new GradeSourceResult.Progress(true, 2, 1));
                });
        assertThat(quizRows).filteredOn(row -> row.sourceId().equals(quiz) && row.studentId().equals(student))
                .singleElement().satisfies(result -> {
                    assertThat(result.earnedPoints()).isEqualByComparingTo("97");
                    assertThat(result.earnedPoints())
                            .isEqualByComparingTo(quizService.result(teacher.getId(), classId, quiz, student).finalScore());
                    assertThat(result.provenance().attemptId()).isEqualTo(hiddenQuizAttempt);
                    assertThat(result.provenance().quizOverrideId()).isNotNull();
                    assertThat(result.published()).isFalse();
                });
        verifyQueries(template, 2);
    }

    @Test
    void distinguishesZeroFromMissingAndUngradedScores() {
        var teacher = account(AccountRole.TEACHER);
        var classId = classroom(teacher);
        var student = member(teacher, classId);
        var emptyAssignment = assignment(classId);
        var ungradedAssignment = assignment(classId);
        jdbc.update("UPDATE tbl_assignment SET type='UNGRADED' WHERE id=?", ungradedAssignment);
        legacyAttempt(ungradedAssignment, student, 1, 0);
        var zeroQuiz = quiz(classId);
        quizAttempt(zeroQuiz, student, 0, true);
        var ungradedQuiz = quiz(classId);
        jdbc.update("UPDATE tbl_quiz SET graded=false WHERE id=?", ungradedQuiz);

        var template = new NamedParameterJdbcTemplate(jdbc);
        var assignments = new AssignmentGradeSource(template);
        var quizzes = new QuizGradeSource(template);
        assertThat(assignments.load(classId, List.of(emptyAssignment), List.of(student), TEACHER))
                .singleElement().satisfies(result -> {
                    assertThat(result.status()).isEqualTo(NOT_SUBMITTED);
                    assertThat(result.earnedPoints()).isNull();
                    assertThat(result.possiblePoints()).isNull();
                });
        assertThat(assignments.load(classId, List.of(ungradedAssignment), List.of(student), TEACHER))
                .singleElement().satisfies(result -> {
                    assertThat(result.status()).isEqualTo(UNGRADED);
                    assertThat(result.earnedPoints()).isNull();
                    assertThat(result.progress().submissionCount()).isEqualTo(1);
                });
        assertThat(quizzes.load(classId, List.of(ungradedQuiz), List.of(student), TEACHER))
                .singleElement().satisfies(result -> {
                    assertThat(result.status()).isEqualTo(UNGRADED);
                    assertThat(result.earnedPoints()).isNull();
                });
        assertThat(quizzes.load(classId, List.of(zeroQuiz), List.of(student), STUDENT))
                .singleElement().satisfies(result -> {
                    assertThat(result.status()).isEqualTo(GRADED);
                    assertThat(result.earnedPoints()).isZero();
                    assertThat(result.possiblePoints()).isEqualByComparingTo("100");
                });
        assertThat(assignments.load(classId, List.of(ungradedAssignment), List.of(student), STUDENT)).isEmpty();
        assertThat(quizzes.load(classId, List.of(ungradedQuiz), List.of(student), STUDENT)).isEmpty();
    }

    @Test
    void pendingAndFailedRejudgeKeepTheLastCompleteEvaluation() {
        var teacher = account(AccountRole.TEACHER);
        var classId = classroom(teacher);
        var student = member(teacher, classId);
        var item = assignment(classId);
        var attempt = hiddenAssignmentEvaluation(item, student);
        jdbc.update("UPDATE tbl_assignment_attempt SET published_evaluation_id=current_evaluation_id WHERE id=?", attempt);
        var source = new AssignmentGradeSource(new NamedParameterJdbcTemplate(jdbc));
        var official = source.load(classId, List.of(item), List.of(student), STUDENT);
        assertThat(official).singleElement().satisfies(result ->
                assertThat(result.earnedPoints()).isEqualByComparingTo("7"));

        var incomplete = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_assignment_evaluation(id,attempt_id,kind,generation,complete,parts)
                VALUES (?,?,'REJUDGE',2,false,'[]'::jsonb)
                """, incomplete, attempt);
        jdbc.update("UPDATE tbl_assignment_attempt SET current_evaluation_id=?,status='SUBMITTED' WHERE id=?", incomplete, attempt);
        var question = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_assignment_question(id,assignment_id,question_type,title,prompt)
                VALUES (?,?,'CODE','Code','Solve')
                """, question, item);
        var job = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_judge_job(id,owner_id,request_key,generation,mode,payload,status)
                VALUES (?,?,?,2,'SUBMIT','{}','QUEUED')
                """, job, student, job.toString());
        jdbc.update("""
                INSERT INTO tbl_assignment_code_job(id,attempt_id,question_id,job_id,generation)
                VALUES (?,?,?,?,2)
                """, UUID.randomUUID(), attempt, question, job);
        assertThat(source.load(classId, List.of(item), List.of(student), TEACHER)).singleElement().satisfies(result -> {
            assertThat(result.earnedPoints()).isEqualByComparingTo("7");
            assertThat(result.provenance()).isEqualTo(official.getFirst().provenance());
            assertThat(result.rejudgePending()).isTrue();
        });
        assertThat(source.load(classId, List.of(item), List.of(student), STUDENT)).isEqualTo(official);

        jdbc.update("UPDATE tbl_judge_job SET status='INFRASTRUCTURE_ERROR' WHERE id=?", job);
        assertThat(source.load(classId, List.of(item), List.of(student), TEACHER)).singleElement().satisfies(result -> {
            assertThat(result.earnedPoints()).isEqualByComparingTo("7");
            assertThat(result.provenance()).isEqualTo(official.getFirst().provenance());
            assertThat(result.rejudgePending()).isFalse();
        });
        assertThat(source.load(classId, List.of(item), List.of(student), STUDENT)).isEqualTo(official);
    }

    @Test
    void olderRejudgeJobsFinishingLateDoNotSupersedeTheNewestAppliedGeneration() {
        var teacher = account(AccountRole.TEACHER);
        var classId = classroom(teacher);
        var student = member(teacher, classId);
        var item = assignment(classId);
        var attempt = evaluatedAttempt(item, student, 1, 7, 10, false, true);
        var question = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_assignment_question(id,assignment_id,question_type,title,prompt)
                VALUES (?,?,'CODE','Code','Solve')
                """, question, item);
        var oldJob = rejudgeJob(student, attempt, question, 2);
        var latestJob = rejudgeJob(student, attempt, question, 3);
        var source = new AssignmentGradeSource(new NamedParameterJdbcTemplate(jdbc));
        assertThat(source.load(classId, List.of(item), List.of(student), TEACHER).getFirst().rejudgePending()).isTrue();

        jdbc.update("UPDATE tbl_judge_job SET status='ACCEPTED' WHERE id=?", latestJob);
        jdbc.update("UPDATE tbl_assignment_code_job SET applied_at=now() WHERE job_id=?", latestJob);
        var latestEvaluation = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_assignment_evaluation(id,attempt_id,kind,generation,final_score,complete,parts)
                VALUES (?,?,'AUTO',2,9,true,'[]'::jsonb)
                """, latestEvaluation, attempt);
        jdbc.update("UPDATE tbl_assignment_attempt SET current_evaluation_id=?,published_evaluation_id=? WHERE id=?",
                latestEvaluation, latestEvaluation, attempt);
        var teacherBefore = source.load(classId, List.of(item), List.of(student), TEACHER);
        var studentBefore = source.load(classId, List.of(item), List.of(student), STUDENT);
        assertThat(teacherBefore).singleElement().satisfies(result -> {
            assertThat(result.rejudgePending()).isFalse();
            assertThat(result.provenance().evaluationId()).isEqualTo(latestEvaluation);
            assertThat(result.earnedPoints()).isEqualByComparingTo("9");
        });
        jdbc.update("UPDATE tbl_judge_job SET status='WRONG_ANSWER' WHERE id=?", oldJob);
        assertThat(source.load(classId, List.of(item), List.of(student), TEACHER)).isEqualTo(teacherBefore);
        assertThat(source.load(classId, List.of(item), List.of(student), STUDENT)).isEqualTo(studentBefore);
    }

    private UUID rejudgeJob(UUID student, UUID attempt, UUID question, int generation) {
        var job = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_judge_job(id,owner_id,request_key,generation,mode,payload,status)
                VALUES (?,?,?,?,'SUBMIT','{}','QUEUED')
                """, job, student, attempt + ":" + question, generation);
        jdbc.update("""
                INSERT INTO tbl_assignment_code_job(id,attempt_id,question_id,job_id,generation)
                VALUES (?,?,?,?,?)
                """, UUID.randomUUID(), attempt, question, job, generation);
        return job;
    }

    @SuppressWarnings("unchecked")
    private static void verifyQueries(NamedParameterJdbcTemplate template, int count) {
        verify(template, times(count)).query(anyString(), any(Map.class), any(RowMapper.class));
    }

    private UUID classroom(AccountEntity teacher) {
        return classrooms.create(teacher.getId(),
                new ClassroomDtos.Create("Grade source class", null, JoinMode.AUTO_JOIN, null, null)).id();
    }

    private UUID member(AccountEntity teacher, UUID classroom) {
        var student = account(AccountRole.STUDENT);
        classrooms.directAdd(teacher.getId(), classroom, new ClassroomDtos.DirectAdd(student.getUsername()));
        return student.getId();
    }

    private AccountEntity account(AccountRole role) {
        var id = UUID.randomUUID();
        var account = new AccountEntity();
        account.setId(id);
        account.setUsername("g1_" + id.toString().replace("-", ""));
        account.setEmail(id + "@example.test");
        account.setPasswordHash("unused");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }

    private UUID assignment(UUID classroom) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_assignment(id,classroom_id,title,status,is_grade_published)
                VALUES (?,?,'Assignment','PUBLISHED',true)
                """, id, classroom);
        return id;
    }

    private UUID legacyAttempt(UUID assignment, UUID student, int number, int score) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_assignment_attempt(id,assignment_id,student_id,attempt_number,status,started_at,submitted_at,final_score)
                VALUES (?,?,?,?,'GRADED',now(),now(),?)
                """, id, assignment, student, number, score);
        return id;
    }

    private UUID hiddenAssignmentEvaluation(UUID assignment, UUID student) {
        return evaluatedAttempt(assignment, student, 2, 7, 10, true, false);
    }

    private UUID evaluatedAttempt(UUID assignment, UUID student, int number, int score, int maximum,
                                  boolean late, boolean published) {
        var id = UUID.randomUUID();
        var evaluation = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_assignment_attempt(id,assignment_id,student_id,attempt_number,status,started_at,submitted_at,
                    is_late,revision_snapshot,snapshot_provenance)
                VALUES (?,?,?,?,'GRADED',now(),now(),?,
                    jsonb_build_object('questions',jsonb_build_array(jsonb_build_object('points',?::integer))),'TRUSTED')
                """, id, assignment, student, number, late, maximum);
        jdbc.update("""
                INSERT INTO tbl_assignment_evaluation(id,attempt_id,kind,generation,auto_score,overridden_score,final_score,complete,parts)
                VALUES (?,?,'OVERRIDE',1,1,?, ?,true,'[]'::jsonb)
                """, evaluation, id, score, score);
        jdbc.update("UPDATE tbl_assignment_attempt SET current_evaluation_id=?,published_evaluation_id=? WHERE id=?",
                evaluation, published ? evaluation : null, id);
        return id;
    }

    private UUID quiz(UUID classroom) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_quiz(id,classroom_id,title,status,graded,max_attempts,questions_json)
                VALUES (?,?,'Quiz','PUBLISHED',true,3,'[]'::jsonb)
                """, id, classroom);
        return id;
    }

    private UUID quizAttempt(UUID quiz, UUID student, int score, boolean published) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_quiz_attempt(id,quiz_id,student_id,status,started_at,submitted_at,score,grade_published,snapshot_json)
                VALUES (?,?,?,'SUBMITTED',now(),now(),?,?,'{}'::jsonb)
                """, id, quiz, student, score, published);
        return id;
    }
}
