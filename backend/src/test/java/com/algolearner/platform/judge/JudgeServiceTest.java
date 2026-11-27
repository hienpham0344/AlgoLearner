package com.algolearner.platform.judge;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static com.algolearner.platform.judge.JudgeDtos.*;
import static com.algolearner.platform.judge.JudgeTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JudgeServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-16T02:00:00Z");
    private final JudgeProblemRepository problems = mock(JudgeProblemRepository.class);
    private final JudgeTestCaseVersionRepository versions = mock(JudgeTestCaseVersionRepository.class);
    private final JudgeTestCaseRepository testCases = mock(JudgeTestCaseRepository.class);
    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final SubmissionEvaluationRepository evaluations = mock(SubmissionEvaluationRepository.class);
    private final SubmissionTestCaseSnapshotRepository snapshots = mock(SubmissionTestCaseSnapshotRepository.class);
    private final SubmissionTestResultRepository results = mock(SubmissionTestResultRepository.class);
    private final RejudgeHistoryRepository rejudges = mock(RejudgeHistoryRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final JudgeAccessPolicy access = mock(JudgeAccessPolicy.class);
    private final JudgeExecutionPort execution = mock(JudgeExecutionPort.class);
    private final RunRateLimiter limiter = mock(RunRateLimiter.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final JudgeService service = new JudgeService(problems, versions, testCases, submissions,
            evaluations, snapshots, results, rejudges, accounts, access, execution, limiter, events,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private UUID actorId;
    private AccountEntity actor;
    private JudgeProblemEntity problem;
    private JudgeTestCaseVersionEntity version;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        actor = account(actorId, AccountRole.STUDENT);
        problem = problem();
        version = version(problem);
        when(accounts.findById(actorId)).thenReturn(Optional.of(actor));
        when(problems.findById(problem.getId())).thenReturn(Optional.of(problem));
        when(versions.findByProblemIdAndActiveTrue(problem.getId())).thenReturn(Optional.of(version));
    }

    @Test
    void runUsesOnlySamplesAndNeverTouchesOfficialSubmissionRepositories() {
        when(limiter.tryAcquire(actorId, NOW)).thenReturn(true);
        when(testCases.findByVersionIdAndVisibilityOrderByOrdinalNumber(version.getId(), TestCaseVisibility.SAMPLE))
                .thenReturn(List.of(testCase(version, TestCaseVisibility.SAMPLE, 1)));
        when(execution.execute(any())).thenReturn(new JudgeExecutionPort.ExecutionResult(
                JudgeStatus.ACCEPTED, "3", null, 12, 32,
                List.of(new JudgeExecutionPort.CaseResult(1, JudgeStatus.ACCEPTED, 12, 32, "3", null)), "isolated"));

        RunResponse response = service.run(actorId,
                new RunRequest(problem.getId(), JudgeLanguage.JAVA, "class Main {}", null));

        assertThat(response.status()).isEqualTo(JudgeStatus.ACCEPTED);
        verify(execution).execute(argThat(request -> !request.official()
                && request.testCases().stream().allMatch(item -> item.visibility() == TestCaseVisibility.SAMPLE)));
        verifyNoInteractions(submissions, evaluations, snapshots, rejudges);
    }

    @Test
    void rateLimitedRunFailsBeforeLoadingTestCasesOrExecuting() {
        when(limiter.tryAcquire(actorId, NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.run(actorId,
                new RunRequest(problem.getId(), JudgeLanguage.PYTHON, "print(1)", null)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo("RATE_LIMIT_EXCEEDED");
        verifyNoInteractions(execution, testCases, submissions);
    }

    @Test
    void submitPersistsOfficialQueuedSubmissionAndFullSnapshotBeforeDispatch() {
        List<JudgeTestCaseEntity> fullCases = List.of(
                testCase(version, TestCaseVisibility.SAMPLE, 1),
                testCase(version, TestCaseVisibility.HIDDEN, 2));
        when(testCases.findByVersionIdOrderByOrdinalNumber(version.getId())).thenReturn(fullCases);
        when(submissions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(evaluations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubmissionResponse response = service.submit(actorId,
                new SubmitRequest(problem.getId(), JudgeLanguage.PYTHON, "print(input())"));

        assertThat(response.status()).isEqualTo(JudgeStatus.QUEUED);
        assertThat(response.testCaseVersionId()).isEqualTo(version.getId());
        verify(submissions).save(argThat(value -> value.isOfficial()
                && value.getStatus() == JudgeStatus.QUEUED
                && value.getSourceCodeSha256().length() == 64));
        verify(snapshots, times(2)).save(any(SubmissionTestCaseSnapshotEntity.class));
        InOrder order = inOrder(snapshots, events);
        order.verify(snapshots, times(2)).save(any());
        order.verify(events).publishEvent(any(SubmissionQueuedEvent.class));
        verifyNoInteractions(execution);
    }

    @Test
    void testStatusFeedbackNeverExposesActualOrHiddenExpectedOutput() {
        SubmissionEntity submission = submission(actor, JudgeStatus.ACCEPTED);
        submission.setFeedbackLevel(JudgeFeedbackLevel.TEST_STATUS);
        SubmissionEvaluationEntity evaluation = evaluation(submission);
        SubmissionTestCaseSnapshotEntity hidden = snapshot(evaluation, TestCaseVisibility.HIDDEN, 1);
        SubmissionTestResultEntity result = new SubmissionTestResultEntity();
        result.setSnapshot(hidden);
        result.setStatus(JudgeStatus.WRONG_ANSWER);
        result.setActualOutput("user output");
        result.setStderrOutput("details");
        when(submissions.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(evaluations.findBySubmissionIdAndCurrentOfficialTrue(submission.getId())).thenReturn(Optional.of(evaluation));
        when(results.findByEvaluationIdOrderBySnapshotOrdinalNumber(evaluation.getId())).thenReturn(List.of(result));

        SubmissionResponse response = service.getSubmission(actorId, submission.getId());

        assertThat(response.cases()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(JudgeStatus.WRONG_ANSWER);
            assertThat(item.actualOutput()).isNull();
            assertThat(item.stderrOutput()).isNull();
        });
    }

    @Test
    void studentCannotReadAnotherStudentsSubmission() {
        AccountEntity other = account(UUID.randomUUID(), AccountRole.STUDENT);
        SubmissionEntity submission = submission(other, JudgeStatus.ACCEPTED);
        when(submissions.findById(submission.getId())).thenReturn(Optional.of(submission));
        doThrow(new TestAppException()).when(access).requireManage(actor, submission.getProblem());

        assertThatThrownBy(() -> service.getSubmission(actorId, submission.getId()))
                .isInstanceOf(AppException.class);
    }

    @Test
    void rejudgeCreatesNewSnapshotAndHistoryWithoutChangingCurrentOfficialStatus() {
        actor.setRole(AccountRole.TEACHER);
        SubmissionEntity submission = submission(account(UUID.randomUUID(), AccountRole.STUDENT), JudgeStatus.WRONG_ANSWER);
        SubmissionEvaluationEntity previous = evaluation(submission);
        previous.setCurrentOfficial(true);
        List<JudgeTestCaseEntity> fullCases = List.of(testCase(version, TestCaseVisibility.HIDDEN, 1));
        when(submissions.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(testCases.findByVersionIdOrderByOrdinalNumber(version.getId())).thenReturn(fullCases);
        when(evaluations.findBySubmissionIdAndCurrentOfficialTrue(submission.getId())).thenReturn(Optional.of(previous));
        when(evaluations.countBySubmissionId(submission.getId())).thenReturn(1L);
        when(evaluations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rejudges.save(any())).thenAnswer(invocation -> {
            RejudgeHistoryEntity history = invocation.getArgument(0);
            history.setId(9L);
            return history;
        });

        RejudgeResponse response = service.requestRejudge(actorId, submission.getId(), new RejudgeRequest("fixed tests"));

        assertThat(submission.getStatus()).isEqualTo(JudgeStatus.WRONG_ANSWER);
        assertThat(response.historyId()).isEqualTo(9L);
        verify(rejudges).save(argThat(history -> history.getPreviousStatus() == JudgeStatus.WRONG_ANSWER
                && history.getStatus() == RejudgeStatus.QUEUED));
        verify(events).publishEvent(new RejudgeQueuedEvent(9L));
    }

    @Test
    void owningTeacherUpdatesLimitsAndFeedbackWithoutChangingProblemContext() {
        actor.setRole(AccountRole.TEACHER);
        problem.setOwner(actor);
        problem.setContextType(JudgeContextType.CLASS_ASSIGNMENT);
        problem.setClassroomId(UUID.randomUUID());
        UUID originalContextId = problem.getContextId();

        ProblemResponse response = service.updateProblem(actorId, problem.getId(),
                new ProblemUpdateRequest(null, null, 3_000, 384, JudgeFeedbackLevel.DETAILED,
                        Set.of(JudgeLanguage.JAVA), null, null, null));

        assertThat(response.timeLimitMs()).isEqualTo(3_000);
        assertThat(response.memoryLimitMb()).isEqualTo(384);
        assertThat(response.feedbackLevel()).isEqualTo(JudgeFeedbackLevel.DETAILED);
        assertThat(response.enabledLanguages()).containsExactly(JudgeLanguage.JAVA);
        assertThat(response.contextId()).isEqualTo(originalContextId);
        assertThat(response.classroomId()).isEqualTo(problem.getClassroomId());
        verify(access).requireManage(actor, problem);
    }

    @Test
    void getProblemReturnsDetailsWhenAuthorized() {
        ProblemResponse response = service.getProblem(actorId, problem.getId());

        assertThat(response.id()).isEqualTo(problem.getId());
        assertThat(response.title()).isEqualTo("Two Sum");
        verify(access).requireExecute(actor, problem);
    }

    @Test
    void getProblemByContextReturnsDetailsWhenAuthorized() {
        when(problems.findByContextTypeAndContextId(problem.getContextType(), problem.getContextId()))
                .thenReturn(Optional.of(problem));

        ProblemResponse response = service.getProblemByContext(actorId, problem.getContextType(), problem.getContextId());

        assertThat(response.id()).isEqualTo(problem.getId());
        verify(access).requireExecute(actor, problem);
    }

    @Test
    void getSampleTestCasesReturnsOnlySamples() {
        when(testCases.findByVersionIdAndVisibilityOrderByOrdinalNumber(version.getId(), TestCaseVisibility.SAMPLE))
                .thenReturn(List.of(testCase(version, TestCaseVisibility.SAMPLE, 1)));

        List<SampleTestCaseResponse> samples = service.getSampleTestCases(actorId, problem.getId());

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().ordinalNumber()).isEqualTo(1);
        assertThat(samples.getFirst().input()).isEqualTo("1 2");
        assertThat(samples.getFirst().expectedOutput()).isEqualTo("3");
        verify(access).requireExecute(actor, problem);
    }

    private static JudgeProblemEntity problem() {
        AccountEntity owner = account(UUID.randomUUID(), AccountRole.ADMIN);
        JudgeProblemEntity problem = new JudgeProblemEntity();
        problem.setId(UUID.randomUUID());
        problem.setOwner(owner);
        problem.setContextType(JudgeContextType.PUBLIC_PROBLEM);
        problem.setContextId(UUID.randomUUID());
        problem.setTitle("Two Sum");
        problem.setFormat(JudgeProblemFormat.STANDARD_IO);
        problem.setTimeLimitMs(2_000);
        problem.setMemoryLimitMb(256);
        problem.setFeedbackLevel(JudgeFeedbackLevel.TEST_STATUS);
        problem.setEnabledLanguages("JAVA,PYTHON");
        problem.setActive(true);
        return problem;
    }

    private static JudgeTestCaseVersionEntity version(JudgeProblemEntity problem) {
        JudgeTestCaseVersionEntity version = new JudgeTestCaseVersionEntity();
        version.setId(UUID.randomUUID());
        version.setProblem(problem);
        version.setVersionNumber(1);
        version.setActive(true);
        return version;
    }

    private static JudgeTestCaseEntity testCase(JudgeTestCaseVersionEntity version,
            TestCaseVisibility visibility, int ordinal) {
        JudgeTestCaseEntity value = new JudgeTestCaseEntity();
        value.setId(UUID.randomUUID());
        value.setVersion(version);
        value.setVisibility(visibility);
        value.setOrdinalNumber(ordinal);
        value.setInputData("1 2");
        value.setExpectedOutput("3");
        value.setWeight(BigDecimal.ONE);
        return value;
    }

    private SubmissionEntity submission(AccountEntity submitter, JudgeStatus status) {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(UUID.randomUUID());
        submission.setProblem(problem);
        submission.setSubmitter(submitter);
        submission.setTestCaseVersion(version);
        submission.setLanguage(JudgeLanguage.JAVA);
        submission.setSourceCode("class Main {}");
        submission.setSourceCodeSha256(JudgeService.sha256(submission.getSourceCode()));
        submission.setStatus(status);
        submission.setOfficial(true);
        submission.setFeedbackLevel(JudgeFeedbackLevel.TEST_STATUS);
        return submission;
    }

    private static SubmissionEvaluationEntity evaluation(SubmissionEntity submission) {
        SubmissionEvaluationEntity evaluation = new SubmissionEvaluationEntity();
        evaluation.setId(UUID.randomUUID());
        evaluation.setSubmission(submission);
        evaluation.setTestCaseVersion(submission.getTestCaseVersion());
        evaluation.setStatus(submission.getStatus());
        return evaluation;
    }

    private static SubmissionTestCaseSnapshotEntity snapshot(SubmissionEvaluationEntity evaluation,
            TestCaseVisibility visibility, int ordinal) {
        SubmissionTestCaseSnapshotEntity snapshot = new SubmissionTestCaseSnapshotEntity();
        snapshot.setId(UUID.randomUUID());
        snapshot.setEvaluation(evaluation);
        snapshot.setVisibility(visibility);
        snapshot.setOrdinalNumber(ordinal);
        snapshot.setInputData("hidden input");
        snapshot.setExpectedOutput("hidden expected");
        snapshot.setWeight(BigDecimal.ONE);
        return snapshot;
    }

    private static AccountEntity account(UUID id, AccountRole role) {
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setUsername(role + id.toString());
        account.setEmail(id + "@test.local");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }

    private static final class TestAppException extends AppException {
        private TestAppException() { super("forbidden", org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION"); }
    }
}
