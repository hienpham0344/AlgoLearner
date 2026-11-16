package com.algolearner.platform.judge;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.*;

import static com.algolearner.platform.judge.JudgeTypes.*;

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

    public JudgeSubmissionProcessor(SubmissionEvaluationRepository evaluations,
            SubmissionTestCaseSnapshotRepository snapshots, SubmissionTestResultRepository results,
            RejudgeHistoryRepository rejudges, JudgeExecutionPort execution,
            ApplicationEventPublisher events, Clock clock) {
        this.evaluations = evaluations;
        this.snapshots = snapshots;
        this.results = results;
        this.rejudges = rejudges;
        this.execution = execution;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void processSubmission(UUID evaluationId) {
        SubmissionEvaluationEntity evaluation = evaluations.findByIdForUpdate(evaluationId).orElse(null);
        if (evaluation == null || evaluation.getTriggerType() != EvaluationTrigger.INITIAL
                || evaluation.getStatus() != JudgeStatus.QUEUED) return;
        SubmissionEntity submission = evaluation.getSubmission();
        evaluation.setStatus(JudgeStatus.JUDGING);
        evaluation.setStartedAt(clock.instant());
        submission.setStatus(JudgeStatus.JUDGING);
        try {
            JudgeExecutionPort.ExecutionResult outcome = execute(evaluation);
            applyEvaluation(evaluation, outcome);
            evaluation.setCurrentOfficial(true);
            applySubmission(submission, evaluation);
        } catch (JudgeProviderException exception) {
            LOG.warn("Initial Judge evaluation failed: evaluationId={}, submissionId={}",
                    evaluation.getId(), submission.getId());
            markJudgeError(evaluation);
            evaluation.setCurrentOfficial(true);
            applySubmission(submission, evaluation);
        }
    }

    @Transactional
    public void processRejudge(long historyId) {
        RejudgeHistoryEntity history = rejudges.findByIdForUpdate(historyId).orElse(null);
        if (history == null || history.getStatus() != RejudgeStatus.QUEUED) return;
        SubmissionEvaluationEntity next = history.getNewEvaluation();
        SubmissionEntity submission = history.getSubmission();
        history.setStatus(RejudgeStatus.RUNNING);
        next.setStatus(JudgeStatus.JUDGING);
        next.setStartedAt(clock.instant());
        try {
            JudgeStatus previousStatus = submission.getStatus();
            JudgeExecutionPort.ExecutionResult outcome = execute(next);
            if (outcome.status() == JudgeStatus.JUDGE_ERROR) {
                throw new JudgeProviderException("Provider returned infrastructure status");
            }
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
        } catch (JudgeProviderException exception) {
            LOG.warn("Rejudge failed: historyId={}, evaluationId={}, submissionId={}",
                    history.getId(), next.getId(), submission.getId());
            markJudgeError(next);
            next.setCurrentOfficial(false);
            history.setStatus(RejudgeStatus.FAILED);
            history.setFailureMessage("Isolated Judge provider failed");
            history.setCompletedAt(clock.instant());
        }
    }

    private JudgeExecutionPort.ExecutionResult execute(SubmissionEvaluationEntity evaluation) {
        SubmissionEntity submission = evaluation.getSubmission();
        List<SubmissionTestCaseSnapshotEntity> testCases = snapshots
                .findByEvaluationIdOrderByOrdinalNumber(evaluation.getId());
        if (testCases.isEmpty()) throw new JudgeProviderException("Evaluation snapshot is empty");
        List<JudgeExecutionPort.TestCase> requestCases = testCases.stream()
                .map(item -> new JudgeExecutionPort.TestCase(item.getOrdinalNumber(), item.getVisibility(),
                        item.getInputData(), item.getExpectedOutput())).toList();
        JudgeProblemEntity problem = submission.getProblem();
        JudgeExecutionPort.ExecutionResult outcome = execution.execute(new JudgeExecutionPort.ExecutionRequest(
                submission.getLanguage(), problem.getFormat(), submission.getSourceCode(), starterCode(problem, submission.getLanguage()),
                submission.getTimeLimitMs(), submission.getMemoryLimitMb(), true, requestCases));
        if (!terminal(outcome.status())) throw new JudgeProviderException("Provider returned a non-terminal status");
        validateCaseResults(testCases, outcome);
        persistCaseResults(evaluation, testCases, outcome.cases());
        return outcome;
    }

    private static void validateCaseResults(List<SubmissionTestCaseSnapshotEntity> expected,
            JudgeExecutionPort.ExecutionResult outcome) {
        Set<Integer> expectedOrdinals = new HashSet<>();
        for (SubmissionTestCaseSnapshotEntity item : expected) expectedOrdinals.add(item.getOrdinalNumber());
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
}
