package com.algolearner.platform.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.*;

import static com.algolearner.platform.judge.JudgeTypes.*;

/**
 * Processes queued submission evaluations and rejudges without holding database
 * row locks or transactions across untrusted sandbox execution.
 */
@Service
@Profile("!mock")
public class JudgeSubmissionProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(JudgeSubmissionProcessor.class);
    private final SubmissionEvaluationRepository evaluations;
    private final SubmissionTestCaseSnapshotRepository snapshots;
    private final SubmissionTestResultRepository results;
    private final RejudgeHistoryRepository rejudges;
    private final JudgeExecutionPort execution;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionOperations transactions;

    @Autowired
    public JudgeSubmissionProcessor(SubmissionEvaluationRepository evaluations,
            SubmissionTestCaseSnapshotRepository snapshots, SubmissionTestResultRepository results,
            RejudgeHistoryRepository rejudges, JudgeExecutionPort execution,
            ApplicationEventPublisher events, Clock clock, TransactionOperations transactions) {
        this.evaluations = evaluations;
        this.snapshots = snapshots;
        this.results = results;
        this.rejudges = rejudges;
        this.execution = execution;
        this.events = events;
        this.clock = clock;
        this.transactions = Objects.requireNonNull(transactions);
    }

    /** Convenience constructor for unit tests that do not supply a Spring TransactionOperations bean. */
    JudgeSubmissionProcessor(SubmissionEvaluationRepository evaluations,
            SubmissionTestCaseSnapshotRepository snapshots, SubmissionTestResultRepository results,
            RejudgeHistoryRepository rejudges, JudgeExecutionPort execution,
            ApplicationEventPublisher events, Clock clock) {
        this(evaluations, snapshots, results, rejudges, execution, events, clock,
                TransactionOperations.withoutTransaction());
    }

    public void processSubmission(UUID evaluationId) {
        ClaimedSubmission claimed = transactions.execute(status -> claimSubmission(evaluationId));
        if (claimed == null) return;

        try {
            JudgeExecutionPort.ExecutionResult outcome = executeOutsideTransaction(claimed.request());
            transactions.executeWithoutResult(status -> completeSubmission(claimed.evaluationId(), outcome));
        } catch (JudgeProviderException exception) {
            LOG.warn("Initial Judge evaluation failed: evaluationId={}, submissionId={}",
                    claimed.evaluationId(), claimed.submissionId());
            transactions.executeWithoutResult(status -> failSubmission(claimed.evaluationId()));
        }
    }

    public void processRejudge(long historyId) {
        ClaimedRejudge claimed = transactions.execute(status -> claimRejudge(historyId));
        if (claimed == null) return;

        try {
            JudgeExecutionPort.ExecutionResult outcome = executeOutsideTransaction(claimed.request());
            if (outcome.status() == JudgeStatus.JUDGE_ERROR) {
                throw new JudgeProviderException("Provider returned infrastructure status");
            }
            transactions.executeWithoutResult(status -> completeRejudge(claimed.historyId(), outcome));
        } catch (JudgeProviderException exception) {
            LOG.warn("Rejudge failed: historyId={}, evaluationId={}, submissionId={}",
                    claimed.historyId(), claimed.evaluationId(), claimed.submissionId());
            transactions.executeWithoutResult(status -> failRejudge(claimed.historyId()));
        }
    }

    private ClaimedSubmission claimSubmission(UUID evaluationId) {
        SubmissionEvaluationEntity evaluation = evaluations.findByIdForUpdate(evaluationId).orElse(null);
        if (evaluation == null || evaluation.getTriggerType() != EvaluationTrigger.INITIAL
                || evaluation.getStatus() != JudgeStatus.QUEUED) {
            return null;
        }
        SubmissionEntity submission = evaluation.getSubmission();
        List<SubmissionTestCaseSnapshotEntity> testCases = snapshots
                .findByEvaluationIdOrderByOrdinalNumber(evaluation.getId());
        JudgeExecutionPort.ExecutionRequest request = buildRequest(submission, testCases);
        evaluation.setStatus(JudgeStatus.JUDGING);
        evaluation.setStartedAt(clock.instant());
        submission.setStatus(JudgeStatus.JUDGING);
        return new ClaimedSubmission(evaluation.getId(), submission.getId(), request);
    }

    private void completeSubmission(UUID evaluationId, JudgeExecutionPort.ExecutionResult outcome) {
        SubmissionEvaluationEntity evaluation = evaluations.findByIdForUpdate(evaluationId).orElse(null);
        if (evaluation == null || evaluation.getStatus() != JudgeStatus.JUDGING) return;

        List<SubmissionTestCaseSnapshotEntity> testCases = snapshots
                .findByEvaluationIdOrderByOrdinalNumber(evaluation.getId());
        persistCaseResults(evaluation, testCases, outcome.cases());
        applyEvaluation(evaluation, outcome);
        evaluation.setCurrentOfficial(true);
        applySubmission(evaluation.getSubmission(), evaluation);
    }

    private void failSubmission(UUID evaluationId) {
        SubmissionEvaluationEntity evaluation = evaluations.findByIdForUpdate(evaluationId).orElse(null);
        if (evaluation == null || evaluation.getStatus() != JudgeStatus.JUDGING) return;

        markJudgeError(evaluation);
        evaluation.setCurrentOfficial(true);
        applySubmission(evaluation.getSubmission(), evaluation);
    }

    private ClaimedRejudge claimRejudge(long historyId) {
        RejudgeHistoryEntity history = rejudges.findByIdForUpdate(historyId).orElse(null);
        if (history == null || history.getStatus() != RejudgeStatus.QUEUED) return null;

        SubmissionEvaluationEntity next = history.getNewEvaluation();
        SubmissionEntity submission = history.getSubmission();
        List<SubmissionTestCaseSnapshotEntity> testCases = snapshots
                .findByEvaluationIdOrderByOrdinalNumber(next.getId());
        JudgeExecutionPort.ExecutionRequest request = buildRequest(submission, testCases);

        history.setStatus(RejudgeStatus.RUNNING);
        next.setStatus(JudgeStatus.JUDGING);
        next.setStartedAt(clock.instant());
        return new ClaimedRejudge(history.getId(), next.getId(), submission.getId(), request);
    }

    private void completeRejudge(long historyId, JudgeExecutionPort.ExecutionResult outcome) {
        RejudgeHistoryEntity history = rejudges.findByIdForUpdate(historyId).orElse(null);
        if (history == null || history.getStatus() != RejudgeStatus.RUNNING) return;

        SubmissionEvaluationEntity next = history.getNewEvaluation();
        SubmissionEntity submission = history.getSubmission();
        JudgeStatus previousStatus = submission.getStatus();

        List<SubmissionTestCaseSnapshotEntity> testCases = snapshots
                .findByEvaluationIdOrderByOrdinalNumber(next.getId());
        persistCaseResults(next, testCases, outcome.cases());
        applyEvaluation(next, outcome);

        if (history.getPreviousEvaluation() != null) {
            history.getPreviousEvaluation().setCurrentOfficial(false);
            evaluations.flush();
        }
        next.setCurrentOfficial(true);
        submission.setTestCaseVersion(next.getTestCaseVersion());
        applySubmission(submission, next);
        history.setNewStatus(next.getStatus());
        history.setStatus(RejudgeStatus.COMPLETED);
        history.setCompletedAt(clock.instant());

        if (previousStatus != next.getStatus()) {
            events.publishEvent(new SubmissionResultChangedEvent(submission.getId(),
                    submission.getSubmitter().getId(), previousStatus, next.getStatus()));
        }
    }

    private void failRejudge(long historyId) {
        RejudgeHistoryEntity history = rejudges.findByIdForUpdate(historyId).orElse(null);
        if (history == null || history.getStatus() != RejudgeStatus.RUNNING) return;

        SubmissionEvaluationEntity next = history.getNewEvaluation();
        markJudgeError(next);
        next.setCurrentOfficial(false);
        history.setStatus(RejudgeStatus.FAILED);
        history.setFailureMessage("Isolated Judge provider failed");
        history.setCompletedAt(clock.instant());
    }

    private JudgeExecutionPort.ExecutionRequest buildRequest(SubmissionEntity submission,
            List<SubmissionTestCaseSnapshotEntity> testCases) {
        if (testCases.isEmpty()) return null;
        List<JudgeExecutionPort.TestCase> requestCases = testCases.stream()
                .map(item -> new JudgeExecutionPort.TestCase(item.getOrdinalNumber(), item.getVisibility(),
                        item.getInputData(), item.getExpectedOutput()))
                .toList();
        JudgeProblemEntity problem = submission.getProblem();
        return new JudgeExecutionPort.ExecutionRequest(
                submission.getLanguage(), problem.getFormat(), submission.getSourceCode(),
                starterCode(problem, submission.getLanguage()),
                submission.getTimeLimitMs(), submission.getMemoryLimitMb(), true, requestCases);
    }

    private JudgeExecutionPort.ExecutionResult executeOutsideTransaction(JudgeExecutionPort.ExecutionRequest request) {
        if (request == null || request.testCases().isEmpty()) {
            throw new JudgeProviderException("Evaluation snapshot is empty");
        }
        JudgeExecutionPort.ExecutionResult outcome = execution.execute(request);
        if (!terminal(outcome.status())) {
            throw new JudgeProviderException("Provider returned a non-terminal status");
        }
        validateCaseResults(request.testCases(), outcome);
        return outcome;
    }

    private static void validateCaseResults(List<JudgeExecutionPort.TestCase> expected,
            JudgeExecutionPort.ExecutionResult outcome) {
        Set<Integer> expectedOrdinals = new HashSet<>();
        for (JudgeExecutionPort.TestCase item : expected) expectedOrdinals.add(item.ordinalNumber());
        Set<Integer> returnedOrdinals = new HashSet<>();
        for (JudgeExecutionPort.CaseResult item : outcome.cases()) {
            if (!expectedOrdinals.contains(item.ordinalNumber()) || !returnedOrdinals.add(item.ordinalNumber())
                    || !terminal(item.status())) {
                throw new JudgeProviderException("Provider returned malformed case results");
            }
        }
        if (outcome.status() == JudgeStatus.ACCEPTED
                && (returnedOrdinals.size() != expectedOrdinals.size()
                || outcome.cases().stream().anyMatch(item -> item.status() != JudgeStatus.ACCEPTED))) {
            throw new JudgeProviderException("Accepted result is not supported by every test case");
        }
    }

    private void persistCaseResults(SubmissionEvaluationEntity evaluation,
            List<SubmissionTestCaseSnapshotEntity> testCases, List<JudgeExecutionPort.CaseResult> caseResults) {
        Map<Integer, SubmissionTestCaseSnapshotEntity> byOrdinal = new HashMap<>();
        for (SubmissionTestCaseSnapshotEntity snapshot : testCases) byOrdinal.put(snapshot.getOrdinalNumber(), snapshot);
        for (JudgeExecutionPort.CaseResult item : caseResults) {
            SubmissionTestCaseSnapshotEntity snapshot = byOrdinal.get(item.ordinalNumber());
            if (snapshot == null) throw new JudgeProviderException("Provider returned an unknown test case");
            SubmissionTestResultEntity result = new SubmissionTestResultEntity();
            result.setId(UUID.randomUUID());
            result.setEvaluation(evaluation);
            result.setSnapshot(snapshot);
            result.setStatus(item.status());
            result.setPassed(item.status() == JudgeStatus.ACCEPTED);
            result.setActualOutput(item.actualOutput());
            result.setStderrOutput(item.stderrOutput());
            result.setExecutionTimeMs(item.executionTimeMs());
            result.setMemoryUsedKb(item.memoryUsedKb());
            results.save(result);
        }
    }

    private void applyEvaluation(SubmissionEvaluationEntity evaluation, JudgeExecutionPort.ExecutionResult outcome) {
        evaluation.setStatus(outcome.status());
        evaluation.setJudgeProvider(outcome.provider());
        evaluation.setExecutionTimeMs(outcome.executionTimeMs());
        evaluation.setMemoryUsedKb(outcome.memoryUsedKb());
        evaluation.setCompilerOutput(outcome.compilerOutput());
        evaluation.setErrorMessage(null);
        evaluation.setCompletedAt(clock.instant());
    }

    private void markJudgeError(SubmissionEvaluationEntity evaluation) {
        evaluation.setStatus(JudgeStatus.JUDGE_ERROR);
        evaluation.setErrorMessage("Isolated Judge provider failed");
        evaluation.setCompletedAt(clock.instant());
    }

    private static void applySubmission(SubmissionEntity submission, SubmissionEvaluationEntity evaluation) {
        submission.setStatus(evaluation.getStatus());
        submission.setExecutionTimeMs(evaluation.getExecutionTimeMs());
        submission.setMemoryUsedKb(evaluation.getMemoryUsedKb());
        submission.setCompilerOutput(evaluation.getCompilerOutput());
        submission.setErrorMessage(evaluation.getErrorMessage());
        submission.setCompletedAt(evaluation.getCompletedAt());
    }

    private static boolean terminal(JudgeStatus status) {
        return status == JudgeStatus.ACCEPTED || status == JudgeStatus.WRONG_ANSWER
                || status == JudgeStatus.COMPILATION_ERROR || status == JudgeStatus.RUNTIME_ERROR
                || status == JudgeStatus.TIME_LIMIT_EXCEEDED || status == JudgeStatus.MEMORY_LIMIT_EXCEEDED
                || status == JudgeStatus.JUDGE_ERROR || status == JudgeStatus.CANCELLED;
    }

    private static String starterCode(JudgeProblemEntity problem, JudgeLanguage language) {
        return language == JudgeLanguage.JAVA ? problem.getStarterCodeJava() : problem.getStarterCodePython();
    }

    private record ClaimedSubmission(UUID evaluationId, UUID submissionId, JudgeExecutionPort.ExecutionRequest request) {}
    private record ClaimedRejudge(long historyId, UUID evaluationId, UUID submissionId, JudgeExecutionPort.ExecutionRequest request) {}
}
