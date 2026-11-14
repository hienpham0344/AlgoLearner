package com.algolearner.platform.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AssignmentControllerTest {

    private final AssignmentService service = mock(AssignmentService.class);
    private AssignmentController controller;

    private Authentication auth;
    private UUID actorId;
    private UUID classId;

    @BeforeEach
    void setUp() {
        controller = new AssignmentController(service);
        actorId = UUID.randomUUID();
        classId = UUID.randomUUID();
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(actorId.toString());
    }

    @Test
    void listAssignments_ReturnsSummaryList() {
        var summary = new AssignmentDtos.AssignmentSummaryView(
                UUID.randomUUID(), classId, "Exam", AssignmentType.GRADED, AssignmentStatus.PUBLISHED,
                Instant.now(), Instant.now().plusSeconds(3600), 60, 1, false, 1, null, Instant.now()
        );
        when(service.list(actorId, classId)).thenReturn(List.of(summary));

        var res = controller.list(auth, classId);
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getData()).hasSize(1);
    }

    @Test
    void startAttempt_ReturnsAttemptView() {
        var asgId = UUID.randomUUID();
        var attemptView = new AssignmentDtos.AttemptView(
                UUID.randomUUID(), asgId, actorId, "Student", 1, AttemptStatus.IN_PROGRESS,
                Instant.now(), null, false, null, null, null, null, null, null, 3600L, List.of()
        );
        when(service.startAttempt(actorId, classId, asgId)).thenReturn(attemptView);

        var res = controller.startAttempt(auth, classId, asgId);
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getData().status()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    @Test
    void submitAttempt_ReturnsSubmittedAttemptView() {
        var asgId = UUID.randomUUID();
        var attId = UUID.randomUUID();
        var attemptView = new AssignmentDtos.AttemptView(
                attId, asgId, actorId, "Student", 1, AttemptStatus.GRADED,
                Instant.now(), Instant.now(), false, BigDecimal.TEN, null, null, BigDecimal.TEN, null, null, null, List.of()
        );
        when(service.submitAttempt(actorId, classId, asgId, attId)).thenReturn(attemptView);

        var res = controller.submitAttempt(auth, classId, asgId, attId);
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getData().status()).isEqualTo(AttemptStatus.GRADED);
    }

    @Test
    void crudAndTeacherActions_EndpointsWork() {
        var asgId = UUID.randomUUID();
        var detail = new AssignmentDtos.AssignmentDetailView(
                asgId, classId, "Title", "Desc", AssignmentType.GRADED, AssignmentStatus.PUBLISHED,
                Instant.now(), Instant.now().plusSeconds(3600), true, BigDecimal.ZERO,
                60, TimeExpiredAction.AUTO_SUBMIT, 1, ScoringPolicy.HIGHEST,
                false, false, GradePublishMode.IMMEDIATE, true, ViewAnswersMode.ALWAYS, CodeFeedbackLevel.DETAILED,
                List.of(), null, BigDecimal.TEN, Instant.now(), Instant.now()
        );

        var createReq = new AssignmentDtos.CreateAssignmentRequest("Title", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(service.create(actorId, classId, createReq)).thenReturn(detail);
        assertThat(controller.create(auth, classId, createReq).isSuccess()).isTrue();

        when(service.get(actorId, classId, asgId)).thenReturn(detail);
        assertThat(controller.get(auth, classId, asgId).isSuccess()).isTrue();

        var updateReq = new AssignmentDtos.UpdateAssignmentRequest("Title 2", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(service.update(actorId, classId, asgId, updateReq)).thenReturn(detail);
        assertThat(controller.update(auth, classId, asgId, updateReq).isSuccess()).isTrue();

        controller.delete(auth, classId, asgId);
        verify(service).delete(actorId, classId, asgId);

        when(service.publish(actorId, classId, asgId)).thenReturn(detail);
        assertThat(controller.publish(auth, classId, asgId).isSuccess()).isTrue();

        when(service.close(actorId, classId, asgId)).thenReturn(detail);
        assertThat(controller.close(auth, classId, asgId).isSuccess()).isTrue();

        controller.publishGrades(auth, classId, asgId);
        verify(service).publishGrades(actorId, classId, asgId);

        var targetClassId = UUID.randomUUID();
        when(service.cloneAssignment(actorId, classId, asgId, targetClassId)).thenReturn(detail);
        assertThat(controller.cloneAssignment(auth, classId, asgId, targetClassId).isSuccess()).isTrue();

        var addQReq = new AssignmentDtos.AddQuestionRequest(QuestionType.SINGLE_CHOICE, "Q1", "Prompt", BigDecimal.TEN, GradingPolicy.ALL_OR_NOTHING, null, null, null, null, List.of());
        var qView = new AssignmentDtos.QuestionView(UUID.randomUUID(), QuestionType.SINGLE_CHOICE, "Q1", "Prompt", BigDecimal.TEN, 0, GradingPolicy.ALL_OR_NOTHING, null, null, null, List.of(), "Exp");
        when(service.addQuestion(actorId, classId, asgId, addQReq)).thenReturn(qView);
        assertThat(controller.addQuestion(auth, classId, asgId, addQReq).isSuccess()).isTrue();

        var qId = UUID.randomUUID();
        controller.deleteQuestion(auth, classId, asgId, qId);
        verify(service).deleteQuestion(actorId, classId, asgId, qId);

        when(service.getActiveAttempt(actorId, classId, asgId)).thenReturn(null);
        assertThat(controller.getActiveAttempt(auth, classId, asgId).isSuccess()).isTrue();

        var attId = UUID.randomUUID();
        var draftReq = new AssignmentDtos.SaveDraftAnswerRequest(qId, "opt", "text", "code", "lang");
        var ansView = new AssignmentDtos.AnswerView(UUID.randomUUID(), qId, "opt", "text", "code", "lang", true, BigDecimal.TEN, "Good");
        when(service.saveDraftAnswer(actorId, classId, asgId, attId, draftReq)).thenReturn(ansView);
        assertThat(controller.saveDraftAnswer(auth, classId, asgId, attId, draftReq).isSuccess()).isTrue();

        when(service.getSubmissions(actorId, classId, asgId)).thenReturn(List.of());
        assertThat(controller.getSubmissions(auth, classId, asgId).isSuccess()).isTrue();

        var gradeEssayReq = new AssignmentDtos.GradeEssayRequest(qId, BigDecimal.TEN, "Good");
        controller.gradeEssay(auth, classId, asgId, attId, gradeEssayReq);
        verify(service).gradeEssay(actorId, classId, asgId, attId, gradeEssayReq);

        var overrideReq = new AssignmentDtos.OverrideGradeRequest(BigDecimal.TEN, "Bonus");
        controller.overrideGrade(auth, classId, asgId, attId, overrideReq);
        verify(service).overrideGrade(actorId, classId, asgId, attId, overrideReq);

        var rejudgeReq = new AssignmentDtos.RejudgeRequest("Recalculate");
        controller.rejudge(auth, classId, asgId, rejudgeReq);
        verify(service).rejudge(actorId, classId, asgId, rejudgeReq);
    }
}
