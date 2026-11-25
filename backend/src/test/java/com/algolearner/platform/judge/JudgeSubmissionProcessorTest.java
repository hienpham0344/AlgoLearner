package com.algolearner.platform.judge;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.algolearner.platform.judge.JudgeTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JudgeSubmissionProcessorTest {
    private static final Instant NOW = Instant.parse("2026-11-16T02:00:00Z");
    private final SubmissionEvaluationRepository evaluations = mock(SubmissionEvaluationRepository.class);
    private final SubmissionTestCaseSnapshotRepository snapshots = mock(SubmissionTestCaseSnapshotRepository.class);
    private final SubmissionTestResultRepository results = mock(SubmissionTestResultRepository.class);
    private final RejudgeHistoryRepository rejudges = mock(RejudgeHistoryRepository.class);
    private final JudgeExecutionPort execution = mock(JudgeExecutionPort.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final JudgeSubmissionProcessor processor = new JudgeSubmissionProcessor(evaluations, snapshots,
            results, rejudges, execution, events, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void providerFailureMarksInitialSubmissionAsJudgeErrorNeverAccepted() {
        SubmissionEntity submission = submission(JudgeStatus.QUEUED);
        SubmissionEvaluationEntity evaluation = evaluation(submission, EvaluationTrigger.INITIAL);
        when(evaluations.findByIdForUpdate(evaluation.getId())).thenReturn(Optional.of(evaluation));
        when(snapshots.findByEvaluationIdOrderByOrdinalNumber(evaluation.getId()))
                .thenReturn(List.of(snapshot(evaluation, 1)));
        when(execution.execute(any())).thenThrow(new JudgeProviderException("sandbox unavailable"));

        processor.processSubmission(evaluation.getId());

        assertThat(evaluation.getStatus()).isEqualTo(JudgeStatus.JUDGE_ERROR);
        assertThat(evaluation.isCurrentOfficial()).isTrue();
        assertThat(submission.getStatus()).isEqualTo(JudgeStatus.JUDGE_ERROR);
        assertThat(submission.getErrorMessage()).doesNotContain("sandbox unavailable");
    }

    @Test
    void acceptedOutcomeWithMissingCaseResultsIsRejectedAsProviderError() {
        SubmissionEntity submission = submission(JudgeStatus.QUEUED);
        SubmissionEvaluationEntity evaluation = evaluation(submission, EvaluationTrigger.INITIAL);
        when(evaluations.findByIdForUpdate(evaluation.getId())).thenReturn(Optional.of(evaluation));
        when(snapshots.findByEvaluationIdOrderByOrdinalNumber(evaluation.getId()))
                .thenReturn(List.of(snapshot(evaluation, 1)));
        when(execution.execute(any())).thenReturn(new JudgeExecutionPort.ExecutionResult(
                JudgeStatus.ACCEPTED, null, null, 1, 1, List.of(), "broken"));

        processor.processSubmission(evaluation.getId());

        assertThat(submission.getStatus()).isEqualTo(JudgeStatus.JUDGE_ERROR);
        verifyNoInteractions(results);
    }

    @Test
    void failedRejudgePreservesPreviousOfficialEvaluationAndSubmissionStatus() {
        SubmissionEntity submission = submission(JudgeStatus.ACCEPTED);
        SubmissionEvaluationEntity previous = evaluation(submission, EvaluationTrigger.INITIAL);
        previous.setStatus(JudgeStatus.ACCEPTED);
        previous.setCurrentOfficial(true);
        SubmissionEvaluationEntity next = evaluation(submission, EvaluationTrigger.REJUDGE);
        RejudgeHistoryEntity history = history(submission, previous, next);
        when(rejudges.findByIdForUpdate(history.getId())).thenReturn(Optional.of(history));
        when(snapshots.findByEvaluationIdOrderByOrdinalNumber(next.getId()))
                .thenReturn(List.of(snapshot(next, 1)));
        when(execution.execute(any())).thenThrow(new JudgeProviderException("timeout"));

        processor.processRejudge(history.getId());

        assertThat(submission.getStatus()).isEqualTo(JudgeStatus.ACCEPTED);
        assertThat(previous.isCurrentOfficial()).isTrue();
        assertThat(next.isCurrentOfficial()).isFalse();
        assertThat(next.getStatus()).isEqualTo(JudgeStatus.JUDGE_ERROR);
        assertThat(history.getStatus()).isEqualTo(RejudgeStatus.FAILED);
        assertThat(history.getNewStatus()).isNull();
    }

    @Test
    void successfulRejudgeSwitchesOfficialEvaluationAfterUniqueIndexFlush() {
        SubmissionEntity submission = submission(JudgeStatus.WRONG_ANSWER);
        SubmissionEvaluationEntity previous = evaluation(submission, EvaluationTrigger.INITIAL);
        previous.setCurrentOfficial(true);
        SubmissionEvaluationEntity next = evaluation(submission, EvaluationTrigger.REJUDGE);
        RejudgeHistoryEntity history = history(submission, previous, next);
        SubmissionTestCaseSnapshotEntity snapshot = snapshot(next, 1);
        when(rejudges.findByIdForUpdate(history.getId())).thenReturn(Optional.of(history));
        when(snapshots.findByEvaluationIdOrderByOrdinalNumber(next.getId())).thenReturn(List.of(snapshot));
        when(execution.execute(any())).thenReturn(new JudgeExecutionPort.ExecutionResult(
                JudgeStatus.ACCEPTED, null, null, 20, 64,
                List.of(new JudgeExecutionPort.CaseResult(1, JudgeStatus.ACCEPTED, 20, 64, "ok", null)), "isolated"));

        processor.processRejudge(history.getId());

        assertThat(previous.isCurrentOfficial()).isFalse();
        assertThat(next.isCurrentOfficial()).isTrue();
        assertThat(submission.getStatus()).isEqualTo(JudgeStatus.ACCEPTED);
        assertThat(history.getStatus()).isEqualTo(RejudgeStatus.COMPLETED);
        verify(evaluations).flush();
        verify(results).save(argThat(result -> result.getSnapshot() == snapshot && result.isPassed()));
        verify(events).publishEvent(new SubmissionResultChangedEvent(submission.getId(),
                submission.getSubmitter().getId(), JudgeStatus.WRONG_ANSWER, JudgeStatus.ACCEPTED));
    }

    @Test
    void sandboxExecutionRunsOutsideDatabaseTransactionsAndCommitsInTwoShortPhases() {
        AtomicBoolean insideTx = new AtomicBoolean(false);
        AtomicInteger txCount = new AtomicInteger(0);
        TransactionOperations recordingTx = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                txCount.incrementAndGet();
                insideTx.set(true);
                try {
                    return action.doInTransaction(new SimpleTransactionStatus());
                } finally {
                    insideTx.set(false);
                }
            }
        };

        JudgeSubmissionProcessor txProcessor = new JudgeSubmissionProcessor(evaluations, snapshots,
                results, rejudges, execution, events, Clock.fixed(NOW, ZoneOffset.UTC), recordingTx);

        SubmissionEntity submission = submission(JudgeStatus.QUEUED);
        SubmissionEvaluationEntity evaluation = evaluation(submission, EvaluationTrigger.INITIAL);
        SubmissionTestCaseSnapshotEntity snap = snapshot(evaluation, 1);
        when(evaluations.findByIdForUpdate(evaluation.getId())).thenReturn(Optional.of(evaluation));
        when(snapshots.findByEvaluationIdOrderByOrdinalNumber(evaluation.getId())).thenReturn(List.of(snap));

        AtomicBoolean wasInTxDuringSandbox = new AtomicBoolean(true);
        when(execution.execute(any())).thenAnswer(inv -> {
            wasInTxDuringSandbox.set(insideTx.get());
            return new JudgeExecutionPort.ExecutionResult(
                    JudgeStatus.ACCEPTED, "ok", null, 15, null,
                    List.of(new JudgeExecutionPort.CaseResult(1, JudgeStatus.ACCEPTED, 15, null, "ok", null)), "docker");
        });

        txProcessor.processSubmission(evaluation.getId());

        assertThat(wasInTxDuringSandbox.get()).isFalse();
        assertThat(txCount.get()).isEqualTo(2);
        assertThat(evaluation.getStatus()).isEqualTo(JudgeStatus.ACCEPTED);
        assertThat(submission.getStatus()).isEqualTo(JudgeStatus.ACCEPTED);
    }

    @Test
    void duplicateWorkerClaimIsIdempotentAndDoesNotReExecuteSandbox() {
        SubmissionEntity submission = submission(JudgeStatus.JUDGING);
        SubmissionEvaluationEntity evaluation = evaluation(submission, EvaluationTrigger.INITIAL);
        evaluation.setStatus(JudgeStatus.JUDGING);
        when(evaluations.findByIdForUpdate(evaluation.getId())).thenReturn(Optional.of(evaluation));

        processor.processSubmission(evaluation.getId());

        verifyNoInteractions(execution, results);
    }

    private static SubmissionEntity submission(JudgeStatus status) {
        JudgeProblemEntity problem = new JudgeProblemEntity();
        problem.setId(UUID.randomUUID());
        problem.setFormat(JudgeProblemFormat.STANDARD_IO);
        problem.setTimeLimitMs(2_000);
        problem.setMemoryLimitMb(256);
        JudgeTestCaseVersionEntity version = new JudgeTestCaseVersionEntity();
        version.setId(UUID.randomUUID());
        version.setProblem(problem);
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(UUID.randomUUID());
        submission.setProblem(problem);
        AccountEntity submitter = new AccountEntity();
        submitter.setId(UUID.randomUUID());
        submitter.setUsername("student");
        submitter.setEmail(UUID.randomUUID() + "@test.local");
        submitter.setRole(AccountRole.STUDENT);
        submitter.setStatus(AccountStatus.ACTIVE);
        submission.setSubmitter(submitter);
        submission.setTestCaseVersion(version);
        submission.setLanguage(JudgeLanguage.JAVA);
        submission.setSourceCode("class Main {}");
        submission.setStatus(status);
        submission.setOfficial(true);
        return submission;
    }

    private static SubmissionEvaluationEntity evaluation(SubmissionEntity submission, EvaluationTrigger trigger) {
        SubmissionEvaluationEntity evaluation = new SubmissionEvaluationEntity();
        evaluation.setId(UUID.randomUUID());
        evaluation.setSubmission(submission);
        evaluation.setTestCaseVersion(submission.getTestCaseVersion());
        evaluation.setTriggerType(trigger);
        evaluation.setStatus(JudgeStatus.QUEUED);
        return evaluation;
    }

    private static SubmissionTestCaseSnapshotEntity snapshot(SubmissionEvaluationEntity evaluation, int ordinal) {
        SubmissionTestCaseSnapshotEntity snapshot = new SubmissionTestCaseSnapshotEntity();
        snapshot.setId(UUID.randomUUID());
        snapshot.setEvaluation(evaluation);
        snapshot.setOrdinalNumber(ordinal);
        snapshot.setVisibility(TestCaseVisibility.HIDDEN);
        snapshot.setInputData("secret");
        snapshot.setExpectedOutput("answer");
        snapshot.setWeight(BigDecimal.ONE);
        return snapshot;
    }

    private static RejudgeHistoryEntity history(SubmissionEntity submission,
            SubmissionEvaluationEntity previous, SubmissionEvaluationEntity next) {
        RejudgeHistoryEntity history = new RejudgeHistoryEntity();
        history.setId(7L);
        history.setSubmission(submission);
        history.setPreviousEvaluation(previous);
        history.setNewEvaluation(next);
        history.setPreviousStatus(submission.getStatus());
        history.setStatus(RejudgeStatus.QUEUED);
        return history;
    }
}
