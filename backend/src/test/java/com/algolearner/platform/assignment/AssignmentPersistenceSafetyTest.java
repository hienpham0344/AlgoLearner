package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.ClassroomStatus;
import com.algolearner.platform.classroom.JoinMode;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.lesson.LessonEntity;
import com.algolearner.platform.lesson.LessonLinkedItemEntity;
import com.algolearner.platform.lesson.LessonLinkedItemRepository;
import com.algolearner.platform.lesson.LessonRepository;
import com.algolearner.platform.lesson.LessonStatus;
import com.algolearner.platform.lesson.LinkedItemType;
import com.algolearner.platform.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=assignment-persistence-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
@Transactional
class AssignmentPersistenceSafetyTest extends PostgresIntegrationTest {
    @Autowired private AssignmentService service;
    @Autowired private AssignmentRepository assignments;
    @Autowired private AssignmentAttemptRepository attempts;
    @Autowired private AssignmentQuestionRepository questions;
    @Autowired private AssignmentQuestionOptionRepository options;
    @Autowired private AssignmentAnswerRepository answers;
    @Autowired private AssignmentEvaluationHistory evaluations;
    @Autowired private AccountRepository accounts;
    @Autowired private ClassroomRepository classrooms;
    @Autowired private LessonRepository lessons;
    @Autowired private LessonLinkedItemRepository linkedItems;
    @Autowired private EntityManager entityManager;
    @Autowired private com.algolearner.platform.classroom.ClassroomMembershipRepository memberships;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void archivedClassroomRejectsTeacherAssignmentCreation() {
        var teacher=account(AccountRole.TEACHER);
        var classroom=classroom(teacher);
        classroom.setStatus(ClassroomStatus.ARCHIVED);classrooms.saveAndFlush(classroom);
        var input=new AssignmentDtos.CreateAssignmentRequest("Archived classroom mutation",null,AssignmentType.GRADED,
                null,null,false,null,null,TimeExpiredAction.AUTO_SUBMIT,1,ScoringPolicy.HIGHEST,
                false,false,GradePublishMode.MANUAL,ViewAnswersMode.AFTER_SUBMIT,CodeFeedbackLevel.SUMMARY);
        assertThatThrownBy(()->service.create(teacher.getId(),classroom.getId(),input)).isInstanceOf(ApiException.class);
    }

    @Test
    void timeoutQueryIncludesExactExpiryAndOverdueAttemptsEvenAfterDeadline() {
        var now = Instant.parse("2026-11-16T12:00:00Z");
        var classroom = classroom(account(AccountRole.TEACHER));
        var student = account(AccountRole.STUDENT);
        var timed = assignment(classroom, 10);
        timed.setDeadline(now.minusSeconds(300));
        timed.setAllowLateSubmission(false);

        var overdue = attempt(timed, student, 1, AttemptStatus.IN_PROGRESS, now.minusSeconds(601));
        var exactExpiry = attempt(timed, student, 2, AttemptStatus.IN_PROGRESS, now.minusSeconds(600));
        var stillOpen = attempt(timed, student, 3, AttemptStatus.IN_PROGRESS, now.minusSeconds(599));
        var submitted = attempt(timed, student, 4, AttemptStatus.SUBMITTED, now.minusSeconds(1200));
        var untimed = attempt(assignment(classroom, null), student, 1,
                AttemptStatus.IN_PROGRESS, now.minusSeconds(3600));
        var zeroDuration = attempt(assignment(classroom, 0), student, 1,
                AttemptStatus.IN_PROGRESS, now.minusSeconds(3600));
        entityManager.flush();
        entityManager.clear();

        var expired = attempts.findExpiredTimedAttemptIds(now, Integer.MAX_VALUE);
        var ownExpiredIds = Set.of(overdue.getId(), exactExpiry.getId(), stillOpen.getId());
        assertThat(expired.stream().filter(ownExpiredIds::contains).toList())
                .containsExactly(overdue.getId(), exactExpiry.getId(), stillOpen.getId());
        assertThat(expired).doesNotContain(submitted.getId(), untimed.getId(), zeroDuration.getId());
        assertThat(attempts.findExpiredTimedAttemptIds(now, 1)).hasSize(1);
        assertThat(attempts.findExpiredTimedAttemptIds(now, 0)).isEmpty();
    }

    @Test
    void archivedLessonQueryUsesBothItemIdentityAndLessonStatus() {
        var classroom = classroom(account(AccountRole.TEACHER));
        var assignment = assignment(classroom, null);
        var lesson = lessons.saveAndFlush(LessonEntity.builder()
                .id(UUID.randomUUID()).classroom(classroom).title("Persistence lesson")
                .status(LessonStatus.PUBLISHED).build());
        linkedItems.saveAndFlush(LessonLinkedItemEntity.builder().id(UUID.randomUUID())
                .lesson(lesson).itemType(LinkedItemType.ASSIGNMENT).itemId(assignment.getId()).build());

        assertThat(linkedItems.existsByItemTypeAndItemIdAndLessonStatus(
                LinkedItemType.ASSIGNMENT, assignment.getId(), LessonStatus.ARCHIVED)).isFalse();
        assertThat(linkedItems.existsByItemTypeAndItemIdAndLessonStatus(
                LinkedItemType.ASSIGNMENT, assignment.getId(), LessonStatus.PUBLISHED)).isTrue();

        lesson.setStatus(LessonStatus.ARCHIVED);
        entityManager.flush();
        entityManager.clear();

        assertThat(linkedItems.existsByItemTypeAndItemIdAndLessonStatus(
                LinkedItemType.ASSIGNMENT, assignment.getId(), LessonStatus.ARCHIVED)).isTrue();
        assertThat(linkedItems.existsByItemTypeAndItemIdAndLessonStatus(
                LinkedItemType.PRACTICE, assignment.getId(), LessonStatus.ARCHIVED)).isFalse();
        assertThat(linkedItems.existsByItemTypeAndItemIdAndLessonStatus(
                LinkedItemType.ASSIGNMENT, UUID.randomUUID(), LessonStatus.ARCHIVED)).isFalse();
    }

    @Test
    void assignmentAndAttemptLockQueriesExecuteInsideATransaction() {
        var classroom = classroom(account(AccountRole.TEACHER));
        var assignment = assignment(classroom, 10);
        var attempt = attempt(assignment, account(AccountRole.STUDENT), 1,
                AttemptStatus.IN_PROGRESS, Instant.parse("2026-11-16T12:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        assertThat(assignments.findForUpdate(assignment.getId(), classroom.getId()))
                .get().extracting(AssignmentEntity::getId).isEqualTo(assignment.getId());
        assertThat(assignments.findForUpdate(assignment.getId(), UUID.randomUUID())).isEmpty();
        assertThat(assignments.findForUpdateById(assignment.getId()))
                .get().extracting(AssignmentEntity::getId).isEqualTo(assignment.getId());
        assertThat(attempts.findAssignmentIdById(attempt.getId())).contains(assignment.getId());
        assertThat(attempts.findForUpdateById(attempt.getId()))
                .get().extracting(AssignmentAttemptEntity::getId).isEqualTo(attempt.getId());
    }

    @Test
    void retiringAQuestionAfterSubmissionPreservesHistoricalAnswer() {
        var teacher = account(AccountRole.TEACHER);
        var classroom = classroom(teacher);
        var assignment = assignment(classroom, null);
        var now = Instant.parse("2026-11-16T12:00:00Z");
        var attempt = attempt(assignment, account(AccountRole.STUDENT), 1,
                AttemptStatus.SUBMITTED, now.minusSeconds(60));
        var question = questions.saveAndFlush(AssignmentQuestionEntity.builder().id(UUID.randomUUID())
                .assignment(assignment).questionType(QuestionType.ESSAY).title("Historical question")
                .prompt("Explain the algorithm").points(BigDecimal.TEN).build());
        var option=options.saveAndFlush(AssignmentQuestionOptionEntity.builder().id(UUID.randomUUID()).question(question)
                .content("Historical option").isCorrect(true).build());
        var answer = answers.saveAndFlush(AssignmentAnswerEntity.builder().id(UUID.randomUUID())
                .attempt(attempt).question(question).textAnswer("Historical response")
                .isDraft(false).lastSavedAt(now).score(new BigDecimal("7.50")).build());
        entityManager.flush();
        entityManager.clear();

        service.deleteQuestion(teacher.getId(),classroom.getId(),assignment.getId(),question.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(questions.findById(question.getId())).get().extracting(AssignmentQuestionEntity::isRetired).isEqualTo(true);
        assertThat(options.findById(option.getId())).get().extracting(AssignmentQuestionOptionEntity::getContent).isEqualTo("Historical option");
        var persisted = answers.findById(answer.getId()).orElseThrow();
        assertThat(persisted.getTextAnswer()).isEqualTo("Historical response");
        assertThat(persisted.getScore()).isEqualByComparingTo("7.50");
        assertThat(persisted.isDraft()).isFalse();
        assertThat(persisted.getQuestion().getId()).isEqualTo(question.getId());
    }

    @Test
    void retiredWorkingQuestionDoesNotChangePublishedOrStartedRevision() {
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);var classroom=classroom(teacher);
        var member=new com.algolearner.platform.classroom.ClassroomMembershipEntity();member.setId(UUID.randomUUID());member.setClassroom(classroom);
        member.setStudent(student);member.setStatus(com.algolearner.platform.classroom.MembershipStatus.ACTIVE);member.setJoinedAt(Instant.now());memberships.saveAndFlush(member);
        var assignment=assignment(classroom,null);assignment.setMaxAttempts(0);
        var first=questions.saveAndFlush(AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment).questionType(QuestionType.ESSAY)
                .title("First").prompt("First prompt").points(BigDecimal.TEN).essayFormat(EssayFormat.TEXT_ONLY).build());
        var second=questions.saveAndFlush(AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment).questionType(QuestionType.ESSAY)
                .title("Second").prompt("Second prompt").points(new BigDecimal("90")).essayFormat(EssayFormat.TEXT_ONLY).displayOrder(1).build());
        service.publish(teacher.getId(),classroom.getId(),assignment.getId());
        var old=service.startAttempt(student.getId(),classroom.getId(),assignment.getId());
        service.deleteQuestion(teacher.getId(),classroom.getId(),assignment.getId(),first.getId());
        assertThat(questions.findByAssignmentIdOrderByDisplayOrderAsc(assignment.getId())).extracting(AssignmentQuestionEntity::getId).containsExactly(second.getId());
        assertThat(service.get(student.getId(),classroom.getId(),assignment.getId()).questions()).hasSize(2);
        assertThat(service.get(teacher.getId(),classroom.getId(),assignment.getId()).questions())
                .extracting(AssignmentDtos.QuestionView::id).containsExactly(second.getId());
        assertThatThrownBy(()->service.updateQuestion(teacher.getId(),classroom.getId(),assignment.getId(),first.getId(),null))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode",ErrorCode.QUESTION_NOT_FOUND.getCode());
        service.saveDraftAnswer(student.getId(),classroom.getId(),assignment.getId(),old.id(),new AssignmentDtos.SaveDraftAnswerRequest(
                first.getId(),null,"Old revision response",null,null,0L,UUID.randomUUID(),null,null));
        service.submitAttempt(student.getId(),classroom.getId(),assignment.getId(),old.id());
        service.gradeEssay(teacher.getId(),classroom.getId(),assignment.getId(),old.id(),new AssignmentDtos.GradeEssayRequest(first.getId(),BigDecimal.TEN,"Full credit"));
        service.gradeEssay(teacher.getId(),classroom.getId(),assignment.getId(),old.id(),new AssignmentDtos.GradeEssayRequest(second.getId(),BigDecimal.ZERO,"Unanswered"));
        assertThat(attempts.findById(old.id()).orElseThrow().getFinalScore()).isEqualByComparingTo("50");
        service.publish(teacher.getId(),classroom.getId(),assignment.getId());
        var next=service.startAttempt(student.getId(),classroom.getId(),assignment.getId());
        assertThat(next.revisionId()).isNotEqualTo(old.revisionId());
        assertThat(next.questions()).extracting(AssignmentDtos.QuestionView::id).containsExactly(second.getId());
        assertThatThrownBy(()->service.saveDraftAnswer(student.getId(),classroom.getId(),assignment.getId(),next.id(),
                new AssignmentDtos.SaveDraftAnswerRequest(first.getId(),null,"Retired response",null,null,0L,UUID.randomUUID(),null,null)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode",ErrorCode.QUESTION_NOT_FOUND.getCode());
        assertThat(answers.findByAttemptIdAndQuestionId(old.id(),first.getId()).orElseThrow().getTextAnswer()).isEqualTo("Old revision response");
    }

    @Test
    void manualGradeAndOverrideAppendDistinctImmutableEvaluations() {
        var teacher = account(AccountRole.TEACHER);
        var classroom = classroom(teacher);
        var assignment = assignment(classroom, null);
        var attempt = attempt(assignment, account(AccountRole.STUDENT), 1, AttemptStatus.SUBMITTED,
                Instant.parse("2026-11-16T12:00:00Z"));
        var essay = questions.saveAndFlush(AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment)
                .questionType(QuestionType.ESSAY).title("Essay").prompt("Explain").points(BigDecimal.TEN).build());
        answers.saveAndFlush(AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(attempt).question(essay)
                .textAnswer("Saved response").isDraft(false).lastSavedAt(Instant.now()).build());
        service.gradeEssay(teacher.getId(), classroom.getId(), assignment.getId(), attempt.getId(),
                new AssignmentDtos.GradeEssayRequest(essay.getId(), new BigDecimal("7"), "Reasoned feedback"));
        service.overrideGrade(teacher.getId(), classroom.getId(), assignment.getId(), attempt.getId(),
                new AssignmentDtos.OverrideGradeRequest(new BigDecimal("9"), "Reviewed marking"));
        entityManager.flush();
        var rows = jdbc.queryForList("SELECT final_score,actor_id,kind FROM tbl_assignment_evaluation WHERE attempt_id=? ORDER BY generation", attempt.getId());
        assertThat(rows).hasSize(2);
        assertThat((BigDecimal) rows.get(0).get("final_score")).isEqualByComparingTo("7");
        assertThat((BigDecimal) rows.get(1).get("final_score")).isEqualByComparingTo("9");
        assertThat(rows.get(0).get("actor_id")).isEqualTo(teacher.getId());
        assertThat(rows.get(1).get("kind")).isEqualTo("OVERRIDE");
    }

    @Test
    void rejudgeNotifiesOnlyWhenChangedOfficialGradeIsPublished() {
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);var classroom=classroom(teacher);
        var assignment=assignment(classroom,null);
        var question=questions.saveAndFlush(AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment)
                .questionType(QuestionType.ESSAY).title("Essay").prompt("Explain").essayFormat(EssayFormat.TEXT_ONLY).points(BigDecimal.TEN).build());
        var snapshot=new AssignmentSnapshotPolicy.Question(question.getId(),QuestionType.ESSAY,"Essay","Explain",BigDecimal.TEN,
                GradingPolicy.ALL_OR_NOTHING,EssayFormat.TEXT_ONLY,null,null,null,0,java.util.List.of(),null);
        var attempt=attemptWithSnapshot(assignment,student,1,java.util.List.of(snapshot));
        service.gradeEssay(teacher.getId(),classroom.getId(),assignment.getId(),attempt.getId(),
                new AssignmentDtos.GradeEssayRequest(question.getId(),new BigDecimal("7"),"First marking"));
        service.publishGrades(teacher.getId(),classroom.getId(),assignment.getId());
        service.gradeEssay(teacher.getId(),classroom.getId(),assignment.getId(),attempt.getId(),
                new AssignmentDtos.GradeEssayRequest(question.getId(),new BigDecimal("4"),"Corrected marking"));
        service.rejudge(teacher.getId(),classroom.getId(),assignment.getId(),new AssignmentDtos.RejudgeRequest("Recheck"));
        assertThat(rejudgeNotifications(assignment.getId())).isEmpty();
        service.publishGrades(teacher.getId(),classroom.getId(),assignment.getId());
        entityManager.flush();
        var notifications=rejudgeNotifications(assignment.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().get("recipient_id")).isEqualTo(student.getId());
        service.publishGrades(teacher.getId(),classroom.getId(),assignment.getId());
        service.rejudge(teacher.getId(),classroom.getId(),assignment.getId(),new AssignmentDtos.RejudgeRequest("Same result"));
        service.publishGrades(teacher.getId(),classroom.getId(),assignment.getId());
        assertThat(rejudgeNotifications(assignment.getId())).hasSize(1);
    }

    @Test
    void changedOfficialCodeVerdictNotifiesEvenWhenQuestionHasZeroPoints() throws Exception {
        var teacher=account(AccountRole.TEACHER);var student=account(AccountRole.STUDENT);var assignment=assignment(classroom(teacher),null);
        var question=codeQuestion(assignment);question.setPoints(BigDecimal.ZERO);questions.saveAndFlush(question);
        var snapshot=new AssignmentSnapshotPolicy.Question(question.getId(),QuestionType.CODE,"Code","Solve",BigDecimal.ZERO,
                GradingPolicy.ALL_OR_NOTHING,null,null,null,"JAVA",0,java.util.List.of(),null);
        var attempt=attemptWithSnapshot(assignment,student,1,java.util.List.of(snapshot));
        var answer=answers.saveAndFlush(AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(attempt).question(question)
                .codeSubmission("class Main {}").isDraft(false).lastSavedAt(Instant.now()).build());
        codeJob(attempt,question,1,"ACCEPTED");service.refreshCodeResults(attempt.getId());
        service.publishGrades(teacher.getId(),assignment.getClassroom().getId(),assignment.getId());
        answer.setScore(null);attempt.setStatus(AttemptStatus.SUBMITTED);attempt.setFinalScore(null);
        evaluations.append(attempt,java.util.List.of(answer),teacher.getId(),"REJUDGE","Recheck execution");
        codeJob(attempt,question,2,"WRONG_ANSWER");service.refreshCodeResults(attempt.getId());
        assertThat(rejudgeNotifications(assignment.getId())).isEmpty();
        service.publishGrades(teacher.getId(),assignment.getClassroom().getId(),assignment.getId());
        assertThat(rejudgeNotifications(assignment.getId())).hasSize(1);
        assertThat(attempt.getFinalScore()).isEqualByComparingTo("0");
    }

    @Test
    void sameScoreJudgeGenerationsAppendEvaluationsOnceWithoutRepeatedPolling() throws Exception {
        var student=account(AccountRole.STUDENT);var assignment=assignment(classroom(account(AccountRole.TEACHER)),null);
        var question=codeQuestion(assignment);
        var attempt=attemptWithSnapshot(assignment,student,1,java.util.List.of(codeSnapshot(question)));
        answers.saveAndFlush(AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(attempt).question(question)
                .codeSubmission("class Main {}").score(BigDecimal.TEN).isDraft(false).lastSavedAt(Instant.now()).build());
        codeJob(attempt,question,1,"ACCEPTED");
        service.refreshCodeResults(attempt.getId());entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_assignment_evaluation WHERE attempt_id=?",Long.class,attempt.getId())).isEqualTo(1);
        service.refreshCodeResults(attempt.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_assignment_evaluation WHERE attempt_id=?",Long.class,attempt.getId())).isEqualTo(1);
        var secondJob=codeJob(attempt,question,2,"ACCEPTED");
        service.refreshCodeResults(attempt.getId());entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_assignment_evaluation WHERE attempt_id=?",Long.class,attempt.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT parts->0->>'judgeJobId' FROM tbl_assignment_evaluation WHERE attempt_id=? AND generation=2",String.class,attempt.getId()))
                .isEqualTo(secondJob.toString());
        assertThat(jdbc.queryForObject("SELECT parts->0->>'judgeGeneration' FROM tbl_assignment_evaluation WHERE attempt_id=? AND generation=2",String.class,attempt.getId()))
                .isEqualTo("2");
        assertThat(attempt.getFinalScore()).isEqualByComparingTo("10");
    }

    @Test
    void judgePollingSkipsAppliedEssayPendingAndUnavailableLatestJobs() throws Exception {
        var student=account(AccountRole.STUDENT);var assignment=assignment(classroom(account(AccountRole.TEACHER)),null);
        var question=codeQuestion(assignment);
        var essay=new AssignmentSnapshotPolicy.Question(UUID.randomUUID(),QuestionType.ESSAY,"Essay","Explain",BigDecimal.TEN,
                GradingPolicy.ALL_OR_NOTHING,EssayFormat.TEXT_ONLY,null,null,null,1,java.util.List.of(),null);
        var snapshot=java.util.List.of(codeSnapshot(question),essay);
        var applied=attemptWithSnapshot(assignment,student,1,snapshot);
        answers.saveAndFlush(AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(applied).question(question)
                .codeSubmission("class Main {}").isDraft(false).lastSavedAt(Instant.now()).build());
        codeJob(applied,question,1,"ACCEPTED");service.refreshCodeResults(applied.getId());
        var failed=attemptWithSnapshot(assignment,student,2,snapshot);codeJob(failed,question,1,"INFRASTRUCTURE_ERROR");
        var queued=attemptWithSnapshot(assignment,student,3,snapshot);codeJob(queued,question,1,"ACCEPTED");codeJob(queued,question,2,"QUEUED");
        var ready=attemptWithSnapshot(assignment,student,4,snapshot);codeJob(ready,question,1,"ACCEPTED");
        entityManager.flush();
        assertThat(attempts.findPendingOfficialAttemptIds()).containsExactly(ready.getId());
    }

    private java.util.List<java.util.Map<String,Object>> rejudgeNotifications(UUID assignment) {
        entityManager.flush();
        return jdbc.queryForList("SELECT recipient_id,title,message FROM tbl_notification WHERE assignment_id=? AND type='ASSIGNMENT_REJUDGE'",assignment);
    }

    private AssignmentAttemptEntity attemptWithSnapshot(AssignmentEntity assignment,AccountEntity student,int number,
            java.util.List<AssignmentSnapshotPolicy.Question> questions) {
        var snapshot=AssignmentSnapshotPolicy.encode(new AssignmentSnapshotPolicy.Content(null,TimeExpiredAction.AUTO_SUBMIT,false,false,questions));
        return attempts.saveAndFlush(AssignmentAttemptEntity.builder().id(UUID.randomUUID()).assignment(assignment).student(student)
                .attemptNumber(number).status(AttemptStatus.SUBMITTED).startedAt(Instant.now().minusSeconds(120-number))
                .submittedAt(Instant.now().minusSeconds(60-number)).snapshotProvenance("TRUSTED").revisionSnapshot(snapshot).build());
    }

    private AssignmentQuestionEntity codeQuestion(AssignmentEntity assignment) {
        return questions.saveAndFlush(AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment)
                .questionType(QuestionType.CODE).title("Code").prompt("Solve").points(BigDecimal.TEN).codeLanguage("JAVA").build());
    }

    private AssignmentSnapshotPolicy.Question codeSnapshot(AssignmentQuestionEntity question) {
        return new AssignmentSnapshotPolicy.Question(question.getId(),QuestionType.CODE,"Code","Solve",BigDecimal.TEN,
                GradingPolicy.ALL_OR_NOTHING,null,null,null,"JAVA",0,java.util.List.of(),null);
    }

    private UUID codeJob(AssignmentAttemptEntity attempt,AssignmentQuestionEntity question,long generation,String status) throws Exception {
        var id=UUID.randomUUID();
        var verdict=com.algolearner.platform.judge.JudgeGateway.Verdict.valueOf(status);
        String result=java.util.Set.of("QUEUED","RUNNING","INFRASTRUCTURE_ERROR").contains(status)?null:
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new com.algolearner.platform.judge.JudgeGateway.EvaluationResult(
                        id,generation,verdict,"ACCEPTED".equals(status)?1:0,1,java.util.List.of(new com.algolearner.platform.judge.JudgeGateway.CaseResult(0,verdict,null,null,null))));
        jdbc.update("INSERT INTO tbl_judge_job(id,owner_id,request_key,generation,mode,payload,status,result) VALUES(?,?,?,?,?,?,?,?)",
                id,attempt.getStudent().getId(),attempt.getId()+":"+question.getId(),generation,"SUBMIT","{}",status,result);
        jdbc.update("INSERT INTO tbl_assignment_code_job(id,attempt_id,question_id,job_id,generation) VALUES(?,?,?,?,?)",
                UUID.randomUUID(),attempt.getId(),question.getId(),id,generation);
        return id;
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("persist" + UUID.randomUUID().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash("unused-test-fixture-hash");
        return accounts.saveAndFlush(account);
    }

    private ClassroomEntity classroom(AccountEntity teacher) {
        var classroom = new ClassroomEntity();
        classroom.setId(UUID.randomUUID());
        classroom.setOwner(teacher);
        classroom.setName("Assignment persistence safety");
        classroom.setStatus(ClassroomStatus.ACTIVE);
        classroom.setClassCode(UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        classroom.setJoiningOpen(true);
        classroom.setJoinMode(JoinMode.AUTO_JOIN);
        return classrooms.saveAndFlush(classroom);
    }

    private AssignmentEntity assignment(ClassroomEntity classroom, Integer durationMinutes) {
        return assignments.saveAndFlush(AssignmentEntity.builder().id(UUID.randomUUID()).classroom(classroom)
                .title("Persistence assignment").status(AssignmentStatus.PUBLISHED)
                .durationMinutes(durationMinutes).build());
    }

    private AssignmentAttemptEntity attempt(AssignmentEntity assignment, AccountEntity student,
            int number, AttemptStatus status, Instant startedAt) {
        return attempts.saveAndFlush(AssignmentAttemptEntity.builder().id(UUID.randomUUID())
                .assignment(assignment).student(student).attemptNumber(number).status(status)
                .startedAt(startedAt).submittedAt(status == AttemptStatus.SUBMITTED ? startedAt.plusSeconds(30) : null)
                .build());
    }
}
