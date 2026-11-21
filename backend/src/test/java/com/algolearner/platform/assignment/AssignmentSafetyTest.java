package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.notification.NotificationService;
import com.algolearner.platform.lesson.LessonLinkedItemRepository;
import com.algolearner.platform.lesson.LessonStatus;
import com.algolearner.platform.lesson.LinkedItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssignmentSafetyTest {
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final AssignmentQuestionRepository questions = mock(AssignmentQuestionRepository.class);
    private final AssignmentQuestionOptionRepository options = mock(AssignmentQuestionOptionRepository.class);
    private final AssignmentAttemptRepository attempts = mock(AssignmentAttemptRepository.class);
    private final AssignmentAnswerRepository answers = mock(AssignmentAnswerRepository.class);
    private final AssignmentRejudgeLogRepository logs = mock(AssignmentRejudgeLogRepository.class);
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final ClassroomMembershipRepository memberships = mock(ClassroomMembershipRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final LessonLinkedItemRepository linkedItems = mock(LessonLinkedItemRepository.class);
    private final Instant now = Instant.parse("2026-11-16T12:00:00Z");
    private final AssignmentService service = new AssignmentService(assignments, questions, options, attempts,
            answers, logs, classrooms, memberships, accounts, notifications, linkedItems, Clock.fixed(now, ZoneOffset.UTC));
    private final AccountEntity teacher = new AccountEntity();
    private final AccountEntity student = new AccountEntity();
    private final ClassroomEntity classroom = new ClassroomEntity();
    private AssignmentEntity assignment;
    private AssignmentAttemptEntity attempt;

    @BeforeEach
    void setUp() {
        teacher.setId(UUID.randomUUID());
        teacher.setStatus(AccountStatus.ACTIVE);
        teacher.setRole(AccountRole.TEACHER);
        student.setId(UUID.randomUUID());
        student.setStatus(AccountStatus.ACTIVE);
        student.setRole(AccountRole.STUDENT);
        student.setUsername("student");
        classroom.setId(UUID.randomUUID());
        classroom.setOwner(teacher);
        classroom.setStatus(ClassroomStatus.ACTIVE);
        assignment = AssignmentEntity.builder().id(UUID.randomUUID()).classroom(classroom).title("Assignment")
                .status(AssignmentStatus.PUBLISHED).durationMinutes(10).build();
        attempt = AssignmentAttemptEntity.builder().id(UUID.randomUUID()).assignment(assignment).student(student)
                .status(AttemptStatus.IN_PROGRESS).startedAt(now.minusSeconds(60)).build();
        var member = new ClassroomMembershipEntity();
        member.setStatus(MembershipStatus.ACTIVE);
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(assignments.findByIdAndClassroomId(assignment.getId(), classroom.getId())).thenReturn(Optional.of(assignment));
        when(assignments.findForUpdate(assignment.getId(), classroom.getId())).thenReturn(Optional.of(assignment));
        when(assignments.findForUpdateById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(assignments.save(any())).thenAnswer(i -> i.getArgument(0));
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId())).thenReturn(Optional.of(member));
        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findAssignmentIdById(attempt.getId())).thenReturn(Optional.of(assignment.getId()));
        when(attempts.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void unansweredEssayMustNotPublishFalseFinalScore() {
        var essay = AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment)
                .questionType(QuestionType.ESSAY).points(java.math.BigDecimal.TEN).build();
        when(questions.findByAssignmentIdOrderByDisplayOrderAsc(assignment.getId())).thenReturn(List.of(essay));
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of());
        submit();
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(attempt.getFinalScore()).isNull();
    }

    @Test
    void attemptGradingUsesOptionsCapturedAtStartAfterAuthorEditsLiveOptions() {
        var question = AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment)
                .questionType(QuestionType.SINGLE_CHOICE).title("Frozen question").prompt("Original prompt")
                .points(java.math.BigDecimal.TEN).gradingPolicy(GradingPolicy.ALL_OR_NOTHING).build();
        var correct = AssignmentQuestionOptionEntity.builder().id(UUID.randomUUID()).question(question)
                .content("Original correct").isCorrect(true).build();
        var incorrect = AssignmentQuestionOptionEntity.builder().id(UUID.randomUUID()).question(question)
                .content("Original incorrect").isCorrect(false).build();
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(questions.findByAssignmentIdOrderByDisplayOrderAsc(assignment.getId())).thenReturn(List.of(question));
        when(options.findByQuestionIdOrderByDisplayOrderAsc(question.getId())).thenReturn(List.of(correct, incorrect));
        service.startAttempt(student.getId(), classroom.getId(), assignment.getId());
        var saved = org.mockito.ArgumentCaptor.forClass(AssignmentAttemptEntity.class);
        verify(attempts).save(saved.capture());
        var started = saved.getValue();
        when(attempts.findForUpdateById(started.getId())).thenReturn(Optional.of(started));
        var answer = AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(started).question(question)
                .selectedOptionIds("[\"" + correct.getId() + "\"]").build();
        when(answers.findByAttemptId(started.getId())).thenReturn(List.of(answer));
        correct.setCorrect(false); incorrect.setCorrect(true); question.setTitle("Changed live title");
        var result = service.submitAttempt(student.getId(), classroom.getId(), assignment.getId(), started.getId());
        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().getFirst().title()).isEqualTo("Frozen question");
        assertThat(result.questions().getFirst().options().getFirst().isCorrect()).isNull();
        assertThat(started.getFinalScore()).isEqualByComparingTo(java.math.BigDecimal.TEN);
    }

    @Test
    void teacherCannotGradeOrOverrideAnUnsubmittedDraft() {
        assertThatThrownBy(() -> service.overrideGrade(teacher.getId(), classroom.getId(), assignment.getId(), attempt.getId(),
                new AssignmentDtos.OverrideGradeRequest(java.math.BigDecimal.TEN, "Draft must remain ungraded")))
                .isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
        assertThat(attempt.getOverriddenScore()).isNull();
    }

    @Test
    void essayScoreCannotExceedTheQuestionMaximum() {
        attempt.setStatus(AttemptStatus.SUBMITTED); attempt.setSubmittedAt(now.minusSeconds(1));
        var essay = AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment)
                .questionType(QuestionType.ESSAY).points(java.math.BigDecimal.TEN).build();
        var answer = AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(attempt).question(essay).build();
        when(questions.findByIdAndAssignmentId(essay.getId(), assignment.getId())).thenReturn(Optional.of(essay));
        when(answers.findByAttemptIdAndQuestionId(attempt.getId(), essay.getId())).thenReturn(Optional.of(answer));
        assertThatThrownBy(() -> service.gradeEssay(teacher.getId(), classroom.getId(), assignment.getId(), attempt.getId(),
                new AssignmentDtos.GradeEssayRequest(essay.getId(), java.math.BigDecimal.valueOf(11), "Too high")))
                .isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
        assertThat(answer.getScore()).isNull();
    }

    @Test
    void lateSubmissionDoesNotDeductEarnedScoreFromLegacyPenaltySetting() {
        assignment.setAllowLateSubmission(true); assignment.setDeadline(now.minusSeconds(30));
        assignment.setLatePenaltyPercent(java.math.BigDecimal.TEN);
        var question = AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment)
                .questionType(QuestionType.SINGLE_CHOICE).points(java.math.BigDecimal.TEN).build();
        var option = AssignmentQuestionOptionEntity.builder().id(UUID.randomUUID()).question(question).isCorrect(true).build();
        when(options.findByQuestionIdOrderByDisplayOrderAsc(question.getId())).thenReturn(List.of(option));
        var answer = AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(attempt).question(question)
                .selectedOptionIds("[\"" + option.getId() + "\"]").build();
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of(answer));
        submit();
        assertThat(attempt.getFinalScore()).isEqualByComparingTo(java.math.BigDecimal.TEN);
    }

    @Test
    void lockedTimeoutCannotStartAnotherAttemptWithoutTeacherGrant() {
        assignment.setMaxAttempts(0); assignment.setTimeExpiredAction(TimeExpiredAction.LOCK_WITHOUT_SUBMIT);
        attempt.setStatus(AttemptStatus.TIMED_OUT);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(assignment.getId(), student.getId())).thenReturn(List.of(attempt));
        assertThatThrownBy(() -> service.startAttempt(student.getId(), classroom.getId(), assignment.getId()))
                .isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
        verify(attempts, never()).save(any());
    }

    @Test
    void equalDefaultWeightsNormalizeDifferentQuestionMaximaBeforeAggregation() {
        var first=AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment).questionType(QuestionType.SINGLE_CHOICE)
                .title("Small").prompt("Small").points(java.math.BigDecimal.TEN).build();
        var second=AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment).questionType(QuestionType.SINGLE_CHOICE)
                .title("Large").prompt("Large").points(new java.math.BigDecimal("90")).build();
        var correct=AssignmentQuestionOptionEntity.builder().id(UUID.randomUUID()).question(first).content("A").isCorrect(true).build();
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(questions.findByAssignmentIdOrderByDisplayOrderAsc(assignment.getId())).thenReturn(List.of(first,second));
        when(options.findByQuestionIdOrderByDisplayOrderAsc(first.getId())).thenReturn(List.of(correct));
        service.startAttempt(student.getId(),classroom.getId(),assignment.getId());
        var captured=org.mockito.ArgumentCaptor.forClass(AssignmentAttemptEntity.class);verify(attempts).save(captured.capture());
        var started=captured.getValue();
        when(attempts.findForUpdateById(started.getId())).thenReturn(Optional.of(started));
        when(answers.findByAttemptId(started.getId())).thenReturn(List.of(AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(started)
                .question(first).selectedOptionIds("[\""+correct.getId()+"\"]").build()));
        service.submitAttempt(student.getId(),classroom.getId(),assignment.getId(),started.getId());
        assertThat(started.getFinalScore()).isEqualByComparingTo("50");
    }

    @Test
    void staleAutosaveCannotOverwriteANewerAnswer() {
        var question=AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment).questionType(QuestionType.ESSAY).build();
        var answer=AssignmentAnswerEntity.builder().id(UUID.randomUUID()).question(question).attempt(attempt).textAnswer("newer").answerVersion(2).build();
        when(questions.findByIdAndAssignmentId(question.getId(),assignment.getId())).thenReturn(Optional.of(question));
        when(answers.findByAttemptIdAndQuestionId(attempt.getId(),question.getId())).thenReturn(Optional.of(answer));
        var request=new AssignmentDtos.SaveDraftAnswerRequest(question.getId(),null,"stale",null,null,1L,UUID.randomUUID(),null,null);
        assertThatThrownBy(()->service.saveDraftAnswer(student.getId(),classroom.getId(),assignment.getId(),attempt.getId(),request))
                .isInstanceOf(com.algolearner.platform.common.exception.ApiException.class);
        assertThat(answer.getTextAnswer()).isEqualTo("newer");verify(answers,never()).save(any());
    }

    @Test
    void retriedAutosaveMutationReturnsSavedVersionWithoutApplyingPayloadAgain() {
        var mutation=UUID.randomUUID();
        var question=AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment).questionType(QuestionType.ESSAY).build();
        var answer=AssignmentAnswerEntity.builder().id(UUID.randomUUID()).question(question).attempt(attempt).textAnswer("saved")
                .answerVersion(2).clientMutationId(mutation).build();
        when(questions.findByIdAndAssignmentId(question.getId(),assignment.getId())).thenReturn(Optional.of(question));
        when(answers.findByAttemptIdAndQuestionId(attempt.getId(),question.getId())).thenReturn(Optional.of(answer));
        var request=new AssignmentDtos.SaveDraftAnswerRequest(question.getId(),null,"duplicate",null,null,1L,mutation,null,null);
        var result=service.saveDraftAnswer(student.getId(),classroom.getId(),assignment.getId(),attempt.getId(),request);
        assertThat(result.version()).isEqualTo(2);assertThat(result.textAnswer()).isEqualTo("saved");verify(answers,never()).save(any());
    }

    @Test
    void repeatedSubmitReturnsTheSameOfficialAttempt() {
        attempt.setStatus(AttemptStatus.SUBMITTED); attempt.setSubmittedAt(now);
        assertThat(service.submitAttempt(student.getId(), classroom.getId(), assignment.getId(), attempt.getId()).id())
                .isEqualTo(attempt.getId());
        verify(attempts, never()).save(any());
    }

    @Test
    void gradingOneEssayCannotFinalizeWhenAnotherEssayIsUnanswered() {
        attempt.setStatus(AttemptStatus.SUBMITTED); attempt.setSubmittedAt(now);
        var first = AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment)
                .questionType(QuestionType.ESSAY).points(java.math.BigDecimal.TEN).build();
        var second = AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment)
                .questionType(QuestionType.ESSAY).points(java.math.BigDecimal.TEN).build();
        var answer = AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(attempt).question(first).build();
        when(questions.findByIdAndAssignmentId(first.getId(), assignment.getId())).thenReturn(Optional.of(first));
        when(questions.findByAssignmentIdOrderByDisplayOrderAsc(assignment.getId())).thenReturn(List.of(first, second));
        when(answers.findByAttemptIdAndQuestionId(attempt.getId(), first.getId())).thenReturn(Optional.of(answer));
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of(answer));
        service.gradeEssay(teacher.getId(), classroom.getId(), assignment.getId(), attempt.getId(),
                new AssignmentDtos.GradeEssayRequest(first.getId(), java.math.BigDecimal.TEN, "Reviewed"));
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(attempt.getFinalScore()).isNull();
    }

    @Test
    void cannotDeleteQuestionThroughAnotherOwnedClassroom() {
        var foreignAssignmentId = UUID.randomUUID();
        var question = AssignmentQuestionEntity.builder().id(UUID.randomUUID()).build();
        when(questions.findByIdAndAssignmentId(question.getId(), foreignAssignmentId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.deleteQuestion(teacher.getId(), classroom.getId(), foreignAssignmentId, question.getId()))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ASSIGNMENT_NOT_FOUND.getCode());
        verify(questions, never()).delete(any());
        verify(options, never()).deleteAll(any());
    }

    @Test
    void retiringWorkingQuestionPreservesHistoricalEntityAfterStudentsStart() {
        var question = AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment).build();
        when(questions.findByIdAndAssignmentId(question.getId(), assignment.getId())).thenReturn(Optional.of(question));
        when(attempts.existsByAssignmentId(assignment.getId())).thenReturn(true);

        assertThatCode(() -> service.deleteQuestion(teacher.getId(), classroom.getId(), assignment.getId(), question.getId()))
                .doesNotThrowAnyException();
        verify(questions).save(question);
        verify(questions, never()).delete(any());
        verify(options, never()).deleteAll(any());
    }

    @Test
    void studentCannotReadQuestionsBeforeStartTime() {
        assignment.setStartTime(now.plusSeconds(1));

        assertThatThrownBy(() -> service.get(student.getId(), classroom.getId(), assignment.getId()))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ASSIGNMENT_NOT_STARTED.getCode());
        verifyNoInteractions(questions, options);
    }

    @Test
    void studentCanReadQuestionsAtStartTimeAndOwnerCanReadEarlier() {
        assignment.setStartTime(now.plusSeconds(1));
        assertThat(service.get(teacher.getId(), classroom.getId(), assignment.getId()).id()).isEqualTo(assignment.getId());
        assignment.setStartTime(now);
        assertThat(service.get(student.getId(), classroom.getId(), assignment.getId()).id()).isEqualTo(assignment.getId());
    }

    @Test
    void closedAssignmentRejectsExistingAttemptSubmission() {
        assignment.setStatus(AssignmentStatus.CLOSED);
        assertThatThrownBy(this::submit).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ASSIGNMENT_CLOSED.getCode());
        verify(attempts, never()).save(any());
        assertThat(attempt.getSubmittedAt()).isNull();
    }

    @Test
    void archivedClassroomRejectsExistingAttemptSubmission() {
        classroom.setStatus(ClassroomStatus.ARCHIVED);
        assertThatThrownBy(this::submit).isInstanceOf(ApiException.class);
        verify(attempts, never()).save(any());
    }

    @Test
    void inactiveOwnerMakesAssignmentReadOnly() {
        teacher.setStatus(AccountStatus.SUSPENDED);
        assertThatThrownBy(this::submit).isInstanceOf(ApiException.class);
        verify(attempts, never()).save(any());
    }

    @Test
    void lockWithoutSubmitAppliesAtExactServerExpiry() {
        attempt.setStartedAt(now.minusSeconds(600));
        assignment.setTimeExpiredAction(TimeExpiredAction.LOCK_WITHOUT_SUBMIT);
        assertThatThrownBy(this::submit).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TIME_LIMIT_EXCEEDED.getCode());
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.TIMED_OUT);
        assertThat(attempt.getSubmittedAt()).isNull();
        verify(attempts).save(attempt);
        verify(answers, never()).save(any());
    }

    @Test
    void closedAssignmentRejectsAutosaveBeforeModifyingAnswer() {
        assignment.setStatus(AssignmentStatus.CLOSED);
        assertThatThrownBy(() -> service.saveDraftAnswer(student.getId(), classroom.getId(), assignment.getId(), attempt.getId(),
                new AssignmentDtos.SaveDraftAnswerRequest(UUID.randomUUID(), null, "new answer", null, null)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ASSIGNMENT_CLOSED.getCode());
        verifyNoInteractions(questions);
        verify(answers, never()).save(any());
    }

    @Test
    void autoSubmitAfterTimeoutUsesExpiryTimeAndExistingDraftOnly() {
        attempt.setStartedAt(now.minusSeconds(900));
        assignment.setTimeExpiredAction(TimeExpiredAction.AUTO_SUBMIT);
        assignment.setDeadline(now.minusSeconds(60));
        assignment.setAllowLateSubmission(false);
        var draft = AssignmentAnswerEntity.builder().id(UUID.randomUUID()).attempt(attempt)
                .question(AssignmentQuestionEntity.builder().id(UUID.randomUUID()).questionType(QuestionType.ESSAY).build())
                .textAnswer("saved before timeout").isDraft(true).build();
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of(draft));

        assertThatCode(this::submit).doesNotThrowAnyException();
        assertThat(attempt.getSubmittedAt()).isEqualTo(now.minusSeconds(300));
        assertThat(attempt.isLate()).isFalse();
        assertThat(draft.getTextAnswer()).isEqualTo("saved before timeout");
        assertThat(draft.isDraft()).isFalse();
    }

    @Test
    void archivedLinkedLessonRejectsSubmissionAndAutosave() {
        when(linkedItems.existsByItemTypeAndItemIdAndLessonStatus(
                LinkedItemType.ASSIGNMENT, assignment.getId(), LessonStatus.ARCHIVED)).thenReturn(true);
        assertThatThrownBy(this::submit).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ASSIGNMENT_CLOSED.getCode());
        assertThatThrownBy(() -> service.saveDraftAnswer(student.getId(), classroom.getId(), assignment.getId(), attempt.getId(),
                new AssignmentDtos.SaveDraftAnswerRequest(UUID.randomUUID(), null, "blocked", null, null)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.ASSIGNMENT_CLOSED.getCode());
        verify(attempts, never()).save(any());
        verify(answers, never()).save(any());
    }

    @Test
    void expiredAutosaveCommitsTimeoutWithoutSavingIncomingAnswer() {
        attempt.setStartedAt(now.minusSeconds(600));
        assignment.setTimeExpiredAction(TimeExpiredAction.AUTO_SUBMIT);
        var tx = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(tx.getTransaction(any())).thenReturn(status);
        var proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        var proxy = (AssignmentService) proxyFactory.getProxy();

        assertThatThrownBy(() -> proxy.saveDraftAnswer(student.getId(), classroom.getId(), assignment.getId(), attempt.getId(),
                new AssignmentDtos.SaveDraftAnswerRequest(UUID.randomUUID(), null, "too late", null, null)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.TIME_LIMIT_EXCEEDED.getCode());
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(attempt.getSubmittedAt()).isEqualTo(now);
        verify(tx).commit(status);
        verify(tx, never()).rollback(any());
        verify(questions, never()).findByIdAndAssignmentId(any(), any());
        verify(answers, never()).save(any());
    }

    @Test
    void schedulerAutoSubmitsWithoutBrowserAndDoesNotResubmit() {
        attempt.setStartedAt(now.minusSeconds(900));
        service.expireAttempt(attempt.getId());
        service.expireAttempt(attempt.getId());
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(attempt.getSubmittedAt()).isEqualTo(now.minusSeconds(300));
        verify(attempts).save(attempt);
    }

    @Test
    void schedulerLocksWithoutTurningDraftIntoOfficialSubmission() {
        assignment.setTimeExpiredAction(TimeExpiredAction.LOCK_WITHOUT_SUBMIT);
        attempt.setStartedAt(now.minusSeconds(600));
        service.expireAttempt(attempt.getId());
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.TIMED_OUT);
        assertThat(attempt.getSubmittedAt()).isNull();
        verify(answers, never()).save(any());
    }

    @Test
    void schedulerCannotCreateSubmissionWhenClassroomIsArchived() {
        classroom.setStatus(ClassroomStatus.ARCHIVED);
        attempt.setStartedAt(now.minusSeconds(600));
        service.expireAttempt(attempt.getId());
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.TIMED_OUT);
        assertThat(attempt.getSubmittedAt()).isNull();
    }

    @Test
    void lockModeStillAllowsSubmissionOneNanosecondBeforeExpiry() {
        assignment.setTimeExpiredAction(TimeExpiredAction.LOCK_WITHOUT_SUBMIT);
        attempt.setStartedAt(now.minusSeconds(600).plusNanos(1));
        assertThatCode(this::submit).doesNotThrowAnyException();
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(attempt.getSubmittedAt()).isEqualTo(now);
    }

    @Test
    void putCanExplicitlyClearOptionalStartAndDeadline() {
        assignment.setStartTime(now.minusSeconds(60));assignment.setDeadline(now.plusSeconds(600));
        var request=new AssignmentDtos.UpdateAssignmentRequest("Assignment",null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
        service.update(teacher.getId(),classroom.getId(),assignment.getId(),request);
        assertThat(assignment.getStartTime()).isNull();assertThat(assignment.getDeadline()).isNull();
    }

    @Test
    void cannotShortenOrExtendTimerAfterAttemptsStart() {
        when(attempts.existsByAssignmentId(assignment.getId())).thenReturn(true);
        var request = new AssignmentDtos.UpdateAssignmentRequest("Assignment", null, null, null, null, null,
                null, 1, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(teacher.getId(), classroom.getId(), assignment.getId(), request))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());
        assertThat(assignment.getDurationMinutes()).isEqualTo(10);
    }

    @Test
    void cannotChangeTimeoutPolicyAfterAttemptsStart() {
        when(attempts.existsByAssignmentId(assignment.getId())).thenReturn(true);
        var request = new AssignmentDtos.UpdateAssignmentRequest("Assignment", null, null, null, null, null,
                null, null, TimeExpiredAction.LOCK_WITHOUT_SUBMIT, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(teacher.getId(), classroom.getId(), assignment.getId(), request))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());
        assertThat(assignment.getTimeExpiredAction()).isEqualTo(TimeExpiredAction.AUTO_SUBMIT);
    }

    private void submit() {
        service.submitAttempt(student.getId(), classroom.getId(), assignment.getId(), attempt.getId());
    }
}
