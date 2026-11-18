package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.ClassroomStatus;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ResourceNotFoundException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.notification.NotificationService;
import com.algolearner.platform.lesson.LessonLinkedItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AssignmentServiceTest {

    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final AssignmentQuestionRepository questions = mock(AssignmentQuestionRepository.class);
    private final AssignmentQuestionOptionRepository options = mock(AssignmentQuestionOptionRepository.class);
    private final AssignmentAttemptRepository attempts = mock(AssignmentAttemptRepository.class);
    private final AssignmentAnswerRepository answers = mock(AssignmentAnswerRepository.class);
    private final AssignmentRejudgeLogRepository rejudgeLogs = mock(AssignmentRejudgeLogRepository.class);
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final ClassroomMembershipRepository memberships = mock(ClassroomMembershipRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final LessonLinkedItemRepository linkedItems = mock(LessonLinkedItemRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    private AssignmentService service;

    private AccountEntity teacher;
    private AccountEntity student;
    private ClassroomEntity classroom;
    private AssignmentEntity assignment;

    @BeforeEach
    void setUp() {
        service = new AssignmentService(
                assignments, questions, options, attempts, answers, rejudgeLogs,
                classrooms, memberships, accounts, notifications, linkedItems, clock
        );

        teacher = new AccountEntity();
        teacher.setId(UUID.randomUUID());
        teacher.setUsername("teacher");
        teacher.setFullName("Teacher");
        teacher.setRole(AccountRole.TEACHER);
        teacher.setStatus(AccountStatus.ACTIVE);

        student = new AccountEntity();
        student.setId(UUID.randomUUID());
        student.setUsername("student");
        student.setFullName("Student");
        student.setRole(AccountRole.STUDENT);
        student.setStatus(AccountStatus.ACTIVE);

        classroom = new ClassroomEntity();
        classroom.setId(UUID.randomUUID());
        classroom.setName("Algorithms");
        classroom.setOwner(teacher);
        classroom.setStatus(ClassroomStatus.ACTIVE);

        assignment = AssignmentEntity.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .title("Midterm Exam")
                .type(AssignmentType.GRADED)
                .status(AssignmentStatus.PUBLISHED)
                .maxAttempts(1)
                .durationMinutes(60)
                .startTime(clock.instant().minusSeconds(3600))
                .deadline(clock.instant().plusSeconds(3600))
                .allowLateSubmission(false)
                .latePenaltyPercent(BigDecimal.ZERO)
                .build();
        when(assignments.findForUpdate(assignment.getId(), classroom.getId())).thenReturn(Optional.of(assignment));
    }

    @Test
    void startAttempt_Success_CreatesAttempt() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(assignments.findByIdAndClassroomId(assignment.getId(), classroom.getId()))
                .thenReturn(Optional.of(assignment));

        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        when(attempts.findFirstByAssignmentIdAndStudentIdAndStatus(assignment.getId(), student.getId(), AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attempts.countByAssignmentIdAndStudentId(assignment.getId(), student.getId())).thenReturn(0L);
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(attempts.save(any(AssignmentAttemptEntity.class))).thenAnswer(i -> i.getArgument(0));

        var attemptView = service.startAttempt(student.getId(), classroom.getId(), assignment.getId());
        assertThat(attemptView).isNotNull();
        assertThat(attemptView.attemptNumber()).isEqualTo(1);
        assertThat(attemptView.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(attemptView.remainingSeconds()).isGreaterThan(0L);
    }

    @Test
    void startAttempt_ThrowsWhenMaxAttemptsExceeded() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(assignments.findByIdAndClassroomId(assignment.getId(), classroom.getId()))
                .thenReturn(Optional.of(assignment));

        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        when(attempts.findFirstByAssignmentIdAndStudentIdAndStatus(assignment.getId(), student.getId(), AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attempts.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(assignment.getId(), student.getId()))
                .thenReturn(List.of(AssignmentAttemptEntity.builder().id(UUID.randomUUID()).submittedAt(clock.instant()).attemptNumber(1).build()));

        assertThatThrownBy(() -> service.startAttempt(student.getId(), classroom.getId(), assignment.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTEMPT_LIMIT_EXCEEDED.getCode());
    }

    @Test
    void saveDraftAnswer_Idempotent_SavesAnswer() {
        var attempt = AssignmentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(clock.instant())
                .build();

        var qId = UUID.randomUUID();
        var q = AssignmentQuestionEntity.builder()
                .id(qId)
                .assignment(assignment)
                .questionType(QuestionType.ESSAY)
                .title("Essay Question")
                .prompt("Describe Dijkstra algorithm")
                .build();

        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(questions.findByIdAndAssignmentId(qId, assignment.getId())).thenReturn(Optional.of(q));
        when(answers.findByAttemptIdAndQuestionId(attempt.getId(), qId)).thenReturn(Optional.empty());
        when(answers.save(any(AssignmentAnswerEntity.class))).thenAnswer(i -> i.getArgument(0));

        var draftReq = new AssignmentDtos.SaveDraftAnswerRequest(qId, null, "Dijkstra is greedy...", null, null);
        var answerView = service.saveDraftAnswer(student.getId(), classroom.getId(), assignment.getId(), attempt.getId(), draftReq);

        assertThat(answerView.textAnswer()).isEqualTo("Dijkstra is greedy...");
        assertThat(answerView.isDraft()).isTrue();
    }

    @Test
    void submitAttempt_GradesSingleChoiceAutomatically() {
        assignment.setGradePublished(true);
        var attempt = AssignmentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(clock.instant().minusSeconds(100))
                .build();

        var correctOptId = UUID.randomUUID();
        var optCorrect = AssignmentQuestionOptionEntity.builder()
                .id(correctOptId)
                .content("O(V + E)")
                .isCorrect(true)
                .build();

        var q = AssignmentQuestionEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .questionType(QuestionType.SINGLE_CHOICE)
                .points(BigDecimal.valueOf(10.0))
                .options(List.of(optCorrect))
                .build();

        var ans = AssignmentAnswerEntity.builder()
                .id(UUID.randomUUID())
                .attempt(attempt)
                .question(q)
                .selectedOptionIds("[\"" + correctOptId + "\"]")
                .isDraft(true)
                .build();

        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of(ans));
        when(options.findByQuestionIdOrderByDisplayOrderAsc(q.getId())).thenReturn(List.of(optCorrect));
        when(attempts.save(any(AssignmentAttemptEntity.class))).thenAnswer(i -> i.getArgument(0));

        var submittedView = service.submitAttempt(student.getId(), classroom.getId(), assignment.getId(), attempt.getId());

        assertThat(submittedView.status()).isEqualTo(AttemptStatus.GRADED);
        assertThat(submittedView.autoScore()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        assertThat(submittedView.finalScore()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
    }

    @Test
    void overrideGrade_PreservesOriginalScore() {
        var attempt = AssignmentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(AttemptStatus.GRADED)
                .submittedAt(clock.instant())
                .autoScore(BigDecimal.valueOf(7.0))
                .finalScore(BigDecimal.valueOf(7.0))
                .build();

        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));

        var question = AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment).questionType(QuestionType.SINGLE_CHOICE).points(BigDecimal.TEN).build();
        var answer = AssignmentAnswerEntity.builder().id(UUID.randomUUID()).question(question).score(BigDecimal.valueOf(7)).build();
        when(questions.findByAssignmentIdOrderByDisplayOrderAsc(assignment.getId())).thenReturn(List.of(question));
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of(answer));
        var req = new AssignmentDtos.OverrideGradeRequest(BigDecimal.valueOf(9.5), "Bonus points for good explanation");
        service.overrideGrade(teacher.getId(), classroom.getId(), assignment.getId(), attempt.getId(), req);

        verify(attempts, atLeastOnce()).save(argThat(a ->
                a.getAutoScore().compareTo(BigDecimal.valueOf(7.0)) == 0 &&
                a.getOverriddenScore().compareTo(BigDecimal.valueOf(9.5)) == 0 &&
                a.getFinalScore().compareTo(BigDecimal.valueOf(9.5)) == 0 &&
                a.getOverrideReason().equals("Bonus points for good explanation")
        ));
    }

    @Test
    void cloneAssignment_CopiesQuestionsAndOptions() {
        var targetClassroom = new ClassroomEntity();
        targetClassroom.setId(UUID.randomUUID());
        targetClassroom.setOwner(teacher);
        targetClassroom.setStatus(ClassroomStatus.ACTIVE);

        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(classrooms.findById(targetClassroom.getId())).thenReturn(Optional.of(targetClassroom));
        when(assignments.findByIdAndClassroomId(assignment.getId(), classroom.getId()))
                .thenReturn(Optional.of(assignment));
        when(assignments.save(any(AssignmentEntity.class))).thenAnswer(i -> i.getArgument(0));

        var q = AssignmentQuestionEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .questionType(QuestionType.SINGLE_CHOICE)
                .title("Q1")
                .prompt("Prompt 1")
                .points(BigDecimal.TEN)
                .options(new ArrayList<>())
                .build();

        var opt = AssignmentQuestionOptionEntity.builder()
                .id(UUID.randomUUID())
                .question(q)
                .content("Option A")
                .isCorrect(true)
                .build();
        q.getOptions().add(opt);

        when(questions.findByAssignmentIdOrderByDisplayOrderAsc(assignment.getId())).thenReturn(List.of(q));
        when(options.findByQuestionIdOrderByDisplayOrderAsc(q.getId())).thenReturn(List.of(opt));
        when(questions.save(any(AssignmentQuestionEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cloned = service.cloneAssignment(teacher.getId(), classroom.getId(), assignment.getId(), targetClassroom.getId());

        assertThat(cloned).isNotNull();
        assertThat(cloned.classroomId()).isEqualTo(targetClassroom.getId());
        verify(questions).save(argThat(sq -> sq.getTitle().equals("Q1")));
        verify(options).save(argThat(o -> o.getContent().equals("Option A")));
    }

    @Test
    void submitAttempt_GradesMultipleChoice_AllOrNothing() {
        assignment.setGradePublished(true);
        var attempt = AssignmentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(clock.instant())
                .build();

        var opt1Id = UUID.randomUUID();
        var opt2Id = UUID.randomUUID();
        var opt3Id = UUID.randomUUID();

        var opt1 = AssignmentQuestionOptionEntity.builder().id(opt1Id).content("Correct 1").isCorrect(true).build();
        var opt2 = AssignmentQuestionOptionEntity.builder().id(opt2Id).content("Correct 2").isCorrect(true).build();
        var opt3 = AssignmentQuestionOptionEntity.builder().id(opt3Id).content("Wrong").isCorrect(false).build();

        var q = AssignmentQuestionEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .gradingPolicy(GradingPolicy.ALL_OR_NOTHING)
                .points(BigDecimal.valueOf(10.0))
                .build();

        var ans = AssignmentAnswerEntity.builder()
                .id(UUID.randomUUID())
                .attempt(attempt)
                .question(q)
                .selectedOptionIds("[\"" + opt1Id + "\", \"" + opt2Id + "\"]")
                .isDraft(true)
                .build();

        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of(ans));
        when(options.findByQuestionIdOrderByDisplayOrderAsc(q.getId())).thenReturn(List.of(opt1, opt2, opt3));
        when(attempts.save(any(AssignmentAttemptEntity.class))).thenAnswer(i -> i.getArgument(0));

        var view = service.submitAttempt(student.getId(), classroom.getId(), assignment.getId(), attempt.getId());

        assertThat(view.status()).isEqualTo(AttemptStatus.GRADED);
        assertThat(view.autoScore()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        assertThat(view.finalScore()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
    }

    @Test
    void submitAttempt_PreservesEarnedScoreWithoutLatePenalty() {
        assignment.setGradePublished(true);
        assignment.setDeadline(clock.instant().minusSeconds(3600)); // 1 hour ago
        assignment.setAllowLateSubmission(true);
        assignment.setLatePenaltyPercent(BigDecimal.valueOf(20.0)); // 20% penalty

        var attempt = AssignmentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(clock.instant().minusSeconds(100))
                .build();

        var optId = UUID.randomUUID();
        var opt = AssignmentQuestionOptionEntity.builder().id(optId).content("Correct").isCorrect(true).build();
        var q = AssignmentQuestionEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .questionType(QuestionType.SINGLE_CHOICE)
                .points(BigDecimal.valueOf(10.0))
                .build();

        var ans = AssignmentAnswerEntity.builder()
                .id(UUID.randomUUID())
                .attempt(attempt)
                .question(q)
                .selectedOptionIds("[\"" + optId + "\"]")
                .build();

        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of(ans));
        when(options.findByQuestionIdOrderByDisplayOrderAsc(q.getId())).thenReturn(List.of(opt));
        when(attempts.save(any(AssignmentAttemptEntity.class))).thenAnswer(i -> i.getArgument(0));

        var view = service.submitAttempt(student.getId(), classroom.getId(), assignment.getId(), attempt.getId());

        assertThat(view.isLate()).isTrue();
        assertThat(view.submittedAt()).isEqualTo(clock.instant());
        assertThat(view.autoScore()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        assertThat(view.finalScore()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void updateAssignment_ModifiesFields() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(assignments.findByIdAndClassroomId(assignment.getId(), classroom.getId()))
                .thenReturn(Optional.of(assignment));
        when(assignments.save(any(AssignmentEntity.class))).thenAnswer(i -> i.getArgument(0));

        var updateReq = new AssignmentDtos.UpdateAssignmentRequest(
                "Updated Midterm", "New Description", AssignmentType.GRADED,
                clock.instant(), clock.instant().plusSeconds(7200),
                true, BigDecimal.ZERO,
                90, TimeExpiredAction.AUTO_SUBMIT,
                3, ScoringPolicy.HIGHEST,
                true, true,
                GradePublishMode.AFTER_DEADLINE, ViewAnswersMode.AFTER_SUBMIT,
                CodeFeedbackLevel.DETAILED
        );

        var updated = service.update(teacher.getId(), classroom.getId(), assignment.getId(), updateReq);

        assertThat(updated.title()).isEqualTo("Updated Midterm");
        assertThat(updated.maxAttempts()).isEqualTo(3);
        assertThat(updated.durationMinutes()).isEqualTo(90);
    }

    @Test
    void deleteAssignment_BlockedWhenAttemptsExist_ThrowsException() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(assignments.findByIdAndClassroomId(assignment.getId(), classroom.getId()))
                .thenReturn(Optional.of(assignment));
        when(attempts.existsByAssignmentId(assignment.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(teacher.getId(), classroom.getId(), assignment.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());

        verify(assignments, never()).delete(any());
    }

    @Test
    void deleteQuestion_RetiresAuthoringQuestionAndPreservesHistoricalReferences() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        var qId = UUID.randomUUID();
        var q = AssignmentQuestionEntity.builder().id(qId).assignment(assignment).build();
        when(questions.findByIdAndAssignmentId(qId, assignment.getId())).thenReturn(Optional.of(q));
        when(options.findByQuestionIdOrderByDisplayOrderAsc(qId)).thenReturn(List.of());

        service.deleteQuestion(teacher.getId(), classroom.getId(), assignment.getId(), qId);

        assertThat(q.isRetired()).isTrue();
        verify(questions).save(q);
        verify(questions, never()).delete(any());
        verify(options, never()).deleteAll(anyList());
    }

    @Test
    void startAttempt_ThrowsWhenAssignmentClosedByDeadline() {
        assignment.setDeadline(clock.instant().minusSeconds(100));
        assignment.setAllowLateSubmission(false);

        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(assignments.findByIdAndClassroomId(assignment.getId(), classroom.getId()))
                .thenReturn(Optional.of(assignment));

        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.startAttempt(student.getId(), classroom.getId(), assignment.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ASSIGNMENT_CLOSED.getCode());
    }

    @Test
    void list_ReturnsSummariesForOwnerAndStudent() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(assignments.findByClassroomIdOrderByCreatedAtDesc(classroom.getId()))
                .thenReturn(List.of(assignment));

        var ownerList = service.list(teacher.getId(), classroom.getId());
        assertThat(ownerList).hasSize(1);
        assertThat(ownerList.get(0).title()).isEqualTo(assignment.getTitle());

        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));
        when(assignments.findByClassroomIdAndStatusInOrderByCreatedAtDesc(eq(classroom.getId()), anyList()))
                .thenReturn(List.of(assignment));

        var studentList = service.list(student.getId(), classroom.getId());
        assertThat(studentList).hasSize(1);
    }

    @Test
    void get_ReturnsDetailForOwnerAndActiveStudent() {
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(assignments.findByIdAndClassroomId(assignment.getId(), classroom.getId()))
                .thenReturn(Optional.of(assignment));

        var ownerView = service.get(teacher.getId(), classroom.getId(), assignment.getId());
        assertThat(ownerView).isNotNull();
        assertThat(ownerView.title()).isEqualTo(assignment.getTitle());

        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        var studentView = service.get(student.getId(), classroom.getId(), assignment.getId());
        assertThat(studentView).isNotNull();
        assertThat(studentView.title()).isEqualTo(assignment.getTitle());
    }

    @Test
    void closeAndPublishGrades_Success() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(assignments.findByIdAndClassroomId(assignment.getId(), classroom.getId()))
                .thenReturn(Optional.of(assignment));
        when(assignments.save(any(AssignmentEntity.class))).thenAnswer(i -> i.getArgument(0));

        var closed = service.close(teacher.getId(), classroom.getId(), assignment.getId());
        assertThat(closed.status()).isEqualTo(AssignmentStatus.CLOSED);

        service.publishGrades(teacher.getId(), classroom.getId(), assignment.getId());
        verify(assignments, atLeastOnce()).save(argThat(AssignmentEntity::isGradePublished));
    }

    @Test
    void rejudge_RecalculatesScoreAndLogsWithoutPrematureNotification() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(assignments.findByIdAndClassroomId(assignment.getId(), classroom.getId()))
                .thenReturn(Optional.of(assignment));
        when(accounts.findById(teacher.getId())).thenReturn(Optional.of(teacher));

        var attempt = AssignmentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(AttemptStatus.GRADED)
                .submittedAt(clock.instant())
                .autoScore(BigDecimal.valueOf(5.0))
                .finalScore(BigDecimal.valueOf(5.0))
                .build();

        attempt.setRevisionSnapshot(AssignmentSnapshotPolicy.encode(new AssignmentSnapshotPolicy.Content(null,
                TimeExpiredAction.AUTO_SUBMIT,false,false,List.of())));
        attempt.setSnapshotProvenance("TRUSTED");
        when(attempts.findByAssignmentIdOrderByCreatedAtDesc(assignment.getId()))
                .thenReturn(List.of(attempt));
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of());

        var req = new AssignmentDtos.RejudgeRequest("Recalculate with updated test cases");
        service.rejudge(teacher.getId(), classroom.getId(), assignment.getId(), req);

        verify(rejudgeLogs).save(any(AssignmentRejudgeLogEntity.class));
        verifyNoInteractions(notifications);
    }

    @Test
    void overrideGradeRejectsAttemptFromAnotherClassroom() {
        var otherClassroom = new ClassroomEntity();
        otherClassroom.setId(UUID.randomUUID());
        var otherAssignment = AssignmentEntity.builder().id(UUID.randomUUID()).classroom(otherClassroom).build();
        var attempt = AssignmentAttemptEntity.builder().id(UUID.randomUUID())
                .assignment(otherAssignment).student(student).status(AttemptStatus.GRADED).build();
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.overrideGrade(teacher.getId(), classroom.getId(),
                assignment.getId(), attempt.getId(),
                new AssignmentDtos.OverrideGradeRequest(BigDecimal.ONE, "incorrect scope")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(attempts, never()).save(any());
    }

    @Test
    void draftRejectsAttemptFromAnotherAssignmentBeforeSavingAnswer() {
        allowStudent();
        var otherAssignment = AssignmentEntity.builder().id(UUID.randomUUID()).classroom(classroom).build();
        var attempt = AssignmentAttemptEntity.builder().id(UUID.randomUUID())
                .assignment(otherAssignment).student(student).status(AttemptStatus.IN_PROGRESS)
                .startedAt(clock.instant()).build();
        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.saveDraftAnswer(student.getId(), classroom.getId(),
                assignment.getId(), attempt.getId(),
                new AssignmentDtos.SaveDraftAnswerRequest(UUID.randomUUID(), null, "answer", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(answers, never()).save(any());
    }

    @Test
    void codeAnswerRemainsUngradedWithoutJudge() {
        allowStudent();
        var attempt = AssignmentAttemptEntity.builder().id(UUID.randomUUID())
                .assignment(assignment).student(student).status(AttemptStatus.IN_PROGRESS)
                .startedAt(clock.instant()).build();
        var question = AssignmentQuestionEntity.builder().id(UUID.randomUUID())
                .assignment(assignment).questionType(QuestionType.CODE).points(BigDecimal.TEN).build();
        var answer = AssignmentAnswerEntity.builder().id(UUID.randomUUID())
                .attempt(attempt).question(question).codeSubmission("print('hello')").build();
        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of(answer));
        when(attempts.save(any())).thenAnswer(i -> i.getArgument(0));

        var view = service.submitAttempt(student.getId(), classroom.getId(), assignment.getId(), attempt.getId());
        assertThat(view.status()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(attempt.getFinalScore()).isNull();
        assertThat(answer.getScore()).isNull();
    }

    @Test
    void unpublishedGradesAreHiddenFromStudentAttemptResponse() {
        allowStudent();
        assignment.setGradePublished(false);
        var attempt = AssignmentAttemptEntity.builder().id(UUID.randomUUID())
                .assignment(assignment).student(student).status(AttemptStatus.IN_PROGRESS)
                .startedAt(clock.instant()).build();
        var option = AssignmentQuestionOptionEntity.builder().id(UUID.randomUUID()).isCorrect(true).build();
        var question = AssignmentQuestionEntity.builder().id(UUID.randomUUID())
                .assignment(assignment).questionType(QuestionType.SINGLE_CHOICE)
                .points(BigDecimal.TEN).build();
        var answer = AssignmentAnswerEntity.builder().id(UUID.randomUUID())
                .attempt(attempt).question(question)
                .selectedOptionIds("[\"" + option.getId() + "\"]").build();
        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attempts.findForUpdateById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(answers.findByAttemptId(attempt.getId())).thenReturn(List.of(answer));
        when(options.findByQuestionIdOrderByDisplayOrderAsc(question.getId())).thenReturn(List.of(option));
        when(attempts.save(any())).thenAnswer(i -> i.getArgument(0));

        var view = service.submitAttempt(student.getId(), classroom.getId(), assignment.getId(), attempt.getId());
        assertThat(attempt.getFinalScore()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(view.finalScore()).isNull();
        assertThat(view.autoScore()).isNull();
        assertThat(view.answers().getFirst().score()).isNull();
    }

    @Test
    void cloneAssignment_AcquiresWritableLocksInSortedOrderBeforeOwnerChecks() {
        var classroomService = mock(com.algolearner.platform.classroom.ClassroomService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "classroomService", classroomService);

        UUID lowId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID highId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var sourceClassroom = new ClassroomEntity();
        sourceClassroom.setId(highId);
        sourceClassroom.setOwner(teacher);
        sourceClassroom.setStatus(ClassroomStatus.ACTIVE);
        var targetClassroom = new ClassroomEntity();
        targetClassroom.setId(lowId);
        targetClassroom.setOwner(teacher);
        targetClassroom.setStatus(ClassroomStatus.ACTIVE);

        var src = AssignmentEntity.builder()
                .id(UUID.randomUUID())
                .classroom(sourceClassroom)
                .title("Source")
                .type(AssignmentType.GRADED)
                .status(AssignmentStatus.DRAFT)
                .maxAttempts(1)
                .build();

        when(classroomService.lockWritable(lowId)).thenReturn(targetClassroom);
        when(classroomService.lockWritable(highId)).thenReturn(sourceClassroom);
        when(classrooms.findById(highId)).thenReturn(Optional.of(sourceClassroom));
        when(classrooms.findById(lowId)).thenReturn(Optional.of(targetClassroom));
        when(assignments.findForUpdate(src.getId(), highId)).thenReturn(Optional.of(src));
        when(assignments.save(any(AssignmentEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(questions.findByAssignmentIdOrderByDisplayOrderAsc(src.getId())).thenReturn(List.of());

        service.cloneAssignment(teacher.getId(), highId, src.getId(), lowId);

        var order = inOrder(classroomService, classrooms, assignments);
        order.verify(classroomService).lockWritable(lowId);
        order.verify(classroomService).lockWritable(highId);
        order.verify(classrooms).findById(highId);
        order.verify(classrooms).findById(lowId);
        order.verify(assignments).findForUpdate(src.getId(), highId);
    }

    private void allowStudent() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));
    }
}
