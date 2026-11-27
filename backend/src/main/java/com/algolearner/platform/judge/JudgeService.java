package com.algolearner.platform.judge;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.exception.AppException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;

import static com.algolearner.platform.judge.JudgeDtos.*;
import static com.algolearner.platform.judge.JudgeTypes.*;

@Service
@Profile("!mock")
@Transactional(readOnly = true)
public class JudgeService {
    private static final int DEFAULT_TIME_LIMIT_MS = 2_000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;
    private final JudgeProblemRepository problems;
    private final JudgeTestCaseVersionRepository versions;
    private final JudgeTestCaseRepository testCases;
    private final SubmissionRepository submissions;
    private final SubmissionEvaluationRepository evaluations;
    private final SubmissionTestCaseSnapshotRepository snapshots;
    private final SubmissionTestResultRepository results;
    private final RejudgeHistoryRepository rejudges;
    private final AccountRepository accounts;
    private final JudgeAccessPolicy access;
    private final JudgeExecutionPort execution;
    private final RunRateLimiter limiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public JudgeService(JudgeProblemRepository problems, JudgeTestCaseVersionRepository versions,
            JudgeTestCaseRepository testCases, SubmissionRepository submissions,
            SubmissionEvaluationRepository evaluations, SubmissionTestCaseSnapshotRepository snapshots,
            SubmissionTestResultRepository results, RejudgeHistoryRepository rejudges,
            AccountRepository accounts, JudgeAccessPolicy access, JudgeExecutionPort execution,
            RunRateLimiter limiter, ApplicationEventPublisher events, Clock clock) {
        this.problems = problems;
        this.versions = versions;
        this.testCases = testCases;
        this.submissions = submissions;
        this.evaluations = evaluations;
        this.snapshots = snapshots;
        this.results = results;
        this.rejudges = rejudges;
        this.accounts = accounts;
        this.access = access;
        this.execution = execution;
        this.limiter = limiter;
        this.events = events;
        this.clock = clock;
    }

    public RunResponse run(UUID actorId, RunRequest request) {
        AccountEntity actor = activeAccount(actorId);
        JudgeProblemEntity problem = problem(request.problemId());
        access.requireExecute(actor, problem);
        requireLanguage(problem, request.language());
        if (!limiter.tryAcquire(actorId, clock.instant())) {
            throw error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "RUN is limited to 5 requests per minute");
        }
        List<JudgeExecutionPort.TestCase> executionCases;
        if (request.customInput() != null) {
            executionCases = List.of(new JudgeExecutionPort.TestCase(1, TestCaseVisibility.SAMPLE,
                    request.customInput(), null));
        } else {
            JudgeTestCaseVersionEntity version = activeVersion(problem.getId());
            executionCases = testCases.findByVersionIdAndVisibilityOrderByOrdinalNumber(version.getId(), TestCaseVisibility.SAMPLE)
                    .stream().map(JudgeService::executionCase).toList();
            if (executionCases.isEmpty()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,
                    "SAMPLE_TEST_CASES_REQUIRED", "Problem has no sample test cases");
        }
        try {
            JudgeExecutionPort.ExecutionResult outcome = execution.execute(new JudgeExecutionPort.ExecutionRequest(
                    request.language(), problem.getFormat(), request.sourceCode(), starterCode(problem, request.language()),
                    problem.getTimeLimitMs(), problem.getMemoryLimitMb(), false, executionCases));
            return new RunResponse(outcome.status(), outcome.stdout(), outcome.compilerOutput(),
                    outcome.executionTimeMs(), outcome.memoryUsedKb(), outcome.cases().stream()
                    .map(item -> new CaseResult(item.ordinalNumber(), item.status(), item.executionTimeMs(),
                            item.memoryUsedKb(), item.actualOutput(), item.stderrOutput())).toList());
        } catch (JudgeProviderException exception) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "SANDBOX_ERROR", "Isolated Judge provider is unavailable");
        }
    }

    @Transactional
    public SubmissionResponse submit(UUID actorId, SubmitRequest request) {
        AccountEntity actor = activeAccount(actorId);
        JudgeProblemEntity problem = problem(request.problemId());
        access.requireExecute(actor, problem);
        requireLanguage(problem, request.language());
        JudgeTestCaseVersionEntity version = activeVersion(problem.getId());
        List<JudgeTestCaseEntity> sourceCases = allCases(version);

        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(UUID.randomUUID());
        submission.setProblem(problem);
        submission.setSubmitter(actor);
        submission.setTestCaseVersion(version);
        submission.setLanguage(request.language());
        submission.setSourceCode(request.sourceCode());
        submission.setSourceCodeSha256(sha256(request.sourceCode()));
        submission.setStatus(JudgeStatus.QUEUED);
        submission.setOfficial(true);
        submission.setTimeLimitMs(problem.getTimeLimitMs());
        submission.setMemoryLimitMb(problem.getMemoryLimitMb());
        submission.setFeedbackLevel(problem.getFeedbackLevel());
        submissions.save(submission);

        SubmissionEvaluationEntity evaluation = evaluation(submission, version, actor, 1, EvaluationTrigger.INITIAL);
        evaluations.save(evaluation);
        snapshot(evaluation, sourceCases);
        events.publishEvent(new SubmissionQueuedEvent(evaluation.getId()));
        return response(submission, List.of());
    }

    public SubmissionResponse getSubmission(UUID actorId, UUID submissionId) {
        AccountEntity actor = activeAccount(actorId);
        SubmissionEntity submission = submission(submissionId);
        if (!submission.getSubmitter().getId().equals(actorId)) access.requireManage(actor, submission.getProblem());
        List<CaseResult> visibleResults = evaluations.findBySubmissionIdAndCurrentOfficialTrue(submissionId)
                .map(evaluation -> visibleResults(submission, evaluation)).orElseGet(List::of);
        return response(submission, visibleResults);
    }

    @Transactional
    public RejudgeResponse requestRejudge(UUID actorId, UUID submissionId, RejudgeRequest request) {
        AccountEntity actor = activeAccount(actorId);
        SubmissionEntity submission = submission(submissionId);
        access.requireManage(actor, submission.getProblem());
        JudgeTestCaseVersionEntity version = activeVersion(submission.getProblem().getId());
        List<JudgeTestCaseEntity> sourceCases = allCases(version);
        SubmissionEvaluationEntity previous = evaluations.findBySubmissionIdAndCurrentOfficialTrue(submissionId).orElse(null);
        int attempt = Math.toIntExact(evaluations.countBySubmissionId(submissionId) + 1);
        SubmissionEvaluationEntity next = evaluation(submission, version, actor, attempt, EvaluationTrigger.REJUDGE);
        evaluations.save(next);
        snapshot(next, sourceCases);

        RejudgeHistoryEntity history = new RejudgeHistoryEntity();
        history.setSubmission(submission);
        history.setRequestedBy(actor);
        history.setPreviousEvaluation(previous);
        history.setNewEvaluation(next);
        history.setPreviousStatus(submission.getStatus());
        history.setStatus(RejudgeStatus.QUEUED);
        history.setReason(request == null ? null : clean(request.reason()));
        history.setRequestedAt(clock.instant());
        rejudges.save(history);
        events.publishEvent(new RejudgeQueuedEvent(history.getId()));
        return new RejudgeResponse(history.getId(), submissionId, submission.getStatus(), history.getStatus(), history.getRequestedAt());
    }

    @Transactional
    public ProblemResponse createProblem(UUID actorId, ProblemRequest request) {
        AccountEntity actor = activeAccount(actorId);
        if (actor.getRole() == AccountRole.STUDENT) {
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", "Students cannot configure Judge problems");
        }
        if (actor.getRole() == AccountRole.ADMIN && request.contextType() != JudgeContextType.PUBLIC_PROBLEM) {
            throw error(HttpStatus.FORBIDDEN, "ADMIN_CONTEXT_FORBIDDEN", "Admin may configure only public Judge problems");
        }
        if (actor.getRole() == AccountRole.TEACHER && request.contextType() == JudgeContextType.PUBLIC_PROBLEM) {
            throw error(HttpStatus.FORBIDDEN, "TEACHER_CONTEXT_FORBIDDEN", "Teacher may configure only classroom Judge problems");
        }
        boolean publicProblem = request.contextType() == JudgeContextType.PUBLIC_PROBLEM;
        if (publicProblem != (request.classroomId() == null)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_JUDGE_CONTEXT",
                    "Public problems must not have a classroom; classroom problems require one");
        }
        if (problems.findByContextTypeAndContextId(request.contextType(), request.contextId()).isPresent()) {
            throw error(HttpStatus.CONFLICT, "JUDGE_PROBLEM_EXISTS", "A Judge problem already exists for this context");
        }
        JudgeProblemEntity problem = new JudgeProblemEntity();
        problem.setId(UUID.randomUUID());
        problem.setOwner(actor);
        problem.setContextType(request.contextType());
        problem.setContextId(request.contextId());
        problem.setClassroomId(request.classroomId());
        problem.setTitle(request.title().strip());
        problem.setFormat(request.format());
        problem.setTimeLimitMs(request.timeLimitMs() == null ? DEFAULT_TIME_LIMIT_MS : request.timeLimitMs());
        problem.setMemoryLimitMb(request.memoryLimitMb() == null ? DEFAULT_MEMORY_LIMIT_MB : request.memoryLimitMb());
        problem.setFeedbackLevel(request.feedbackLevel());
        problem.setEnabledLanguages(languageCsv(request.enabledLanguages()));
        problem.setStarterCodeJava(request.starterCodeJava());
        problem.setStarterCodePython(request.starterCodePython());
        problem.setActive(true);
        access.requireManage(actor, problem);
        return problemResponse(problems.save(problem));
    }

    @Transactional
    public ProblemResponse updateProblem(UUID actorId, UUID problemId, ProblemUpdateRequest request) {
        AccountEntity actor = activeAccount(actorId);
        JudgeProblemEntity problem = problem(problemId);
        access.requireManage(actor, problem);
        if (request.title() != null) {
            String title = request.title().strip();
            if (title.isEmpty()) throw error(HttpStatus.BAD_REQUEST,
                    "INVALID_PROBLEM_TITLE", "Problem title cannot be blank");
            problem.setTitle(title);
        }
        if (request.format() != null) problem.setFormat(request.format());
        if (request.timeLimitMs() != null) problem.setTimeLimitMs(request.timeLimitMs());
        if (request.memoryLimitMb() != null) problem.setMemoryLimitMb(request.memoryLimitMb());
        if (request.feedbackLevel() != null) problem.setFeedbackLevel(request.feedbackLevel());
        if (request.enabledLanguages() != null) problem.setEnabledLanguages(languageCsv(request.enabledLanguages()));
        if (request.starterCodeJava() != null) problem.setStarterCodeJava(request.starterCodeJava());
        if (request.starterCodePython() != null) problem.setStarterCodePython(request.starterCodePython());
        if (request.active() != null) problem.setActive(request.active());
        return problemResponse(problem);
    }

    public ProblemResponse getProblem(UUID actorId, UUID problemId) {
        AccountEntity actor = activeAccount(actorId);
        JudgeProblemEntity problem = problem(problemId);
        access.requireExecute(actor, problem);
        return problemResponse(problem);
    }

    public ProblemResponse getProblemByContext(UUID actorId, JudgeContextType contextType, UUID contextId) {
        AccountEntity actor = activeAccount(actorId);
        JudgeProblemEntity problem = problems.findByContextTypeAndContextId(contextType, contextId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "Judge problem was not found"));
        access.requireExecute(actor, problem);
        return problemResponse(problem);
    }

    public List<SampleTestCaseResponse> getSampleTestCases(UUID actorId, UUID problemId) {
        AccountEntity actor = activeAccount(actorId);
        JudgeProblemEntity problem = problem(problemId);
        access.requireExecute(actor, problem);
        JudgeTestCaseVersionEntity version = activeVersion(problemId);
        return testCases.findByVersionIdAndVisibilityOrderByOrdinalNumber(version.getId(), TestCaseVisibility.SAMPLE)
                .stream()
                .map(item -> new SampleTestCaseResponse(item.getOrdinalNumber(), item.getInputData(), item.getExpectedOutput()))
                .toList();
    }

    @Transactional
    public TestCaseVersionResponse publishTestCaseVersion(UUID actorId, UUID problemId,
            PublishTestCaseVersionRequest request) {
        AccountEntity actor = activeAccount(actorId);
        JudgeProblemEntity problem = problem(problemId);
        access.requireManage(actor, problem);
        JudgeTestCaseVersionEntity previous = versions.findByProblemIdAndActiveTrue(problemId).orElse(null);
        if (previous != null) {
            previous.setActive(false);
            versions.flush();
        }
        int versionNumber = versions.findTopByProblemIdOrderByVersionNumberDesc(problemId)
                .map(value -> value.getVersionNumber() + 1).orElse(1);
        JudgeTestCaseVersionEntity version = new JudgeTestCaseVersionEntity();
        version.setId(UUID.randomUUID());
        version.setProblem(problem);
        version.setVersionNumber(versionNumber);
        version.setActive(true);
        version.setChecksumSha256(testCaseChecksum(request.testCases()));
        version.setCreatedBy(actor);
        versions.save(version);
        int ordinal = 1;
        for (TestCaseInput input : request.testCases()) {
            JudgeTestCaseEntity testCase = new JudgeTestCaseEntity();
            testCase.setId(UUID.randomUUID());
            testCase.setVersion(version);
            testCase.setOrdinalNumber(ordinal++);
            testCase.setVisibility(input.visibility());
            testCase.setInputData(input.input());
            testCase.setExpectedOutput(input.expectedOutput());
            testCase.setWeight(input.weight() == null ? BigDecimal.ONE : input.weight());
            testCases.save(testCase);
        }
        if (request.rejudgeExistingSubmissions()) {
            for (SubmissionEntity submission : submissions.findByProblemIdOrderByCreatedAtAsc(problemId)) {
                requestRejudge(actorId, submission.getId(), new RejudgeRequest("TEST_CASE_VERSION_CHANGED"));
            }
        }
        return new TestCaseVersionResponse(version.getId(), problemId, versionNumber,
                version.getChecksumSha256(), true, request.testCases().size(), version.getCreatedAt());
    }

    private List<CaseResult> visibleResults(SubmissionEntity submission, SubmissionEvaluationEntity evaluation) {
        if (submission.getFeedbackLevel() == JudgeFeedbackLevel.AGGREGATE) return List.of();
        return results.findByEvaluationIdOrderBySnapshotOrdinalNumber(evaluation.getId()).stream().map(result -> {
            boolean detailed = submission.getFeedbackLevel() == JudgeFeedbackLevel.DETAILED;
            return new CaseResult(result.getSnapshot().getOrdinalNumber(), result.getStatus(),
                    result.getExecutionTimeMs(), result.getMemoryUsedKb(),
                    detailed ? result.getActualOutput() : null, detailed ? result.getStderrOutput() : null);
        }).toList();
    }

    private void snapshot(SubmissionEvaluationEntity evaluation, List<JudgeTestCaseEntity> sourceCases) {
        for (JudgeTestCaseEntity source : sourceCases) {
            SubmissionTestCaseSnapshotEntity item = new SubmissionTestCaseSnapshotEntity();
            item.setId(UUID.randomUUID());
            item.setEvaluation(evaluation);
            item.setSourceTestCase(source);
            item.setOrdinalNumber(source.getOrdinalNumber());
            item.setVisibility(source.getVisibility());
            item.setInputData(source.getInputData());
            item.setExpectedOutput(source.getExpectedOutput());
            item.setWeight(source.getWeight());
            snapshots.save(item);
        }
    }

    private SubmissionEvaluationEntity evaluation(SubmissionEntity submission, JudgeTestCaseVersionEntity version,
            AccountEntity actor, int attempt, EvaluationTrigger trigger) {
        SubmissionEvaluationEntity evaluation = new SubmissionEvaluationEntity();
        evaluation.setId(UUID.randomUUID());
        evaluation.setSubmission(submission);
        evaluation.setTestCaseVersion(version);
        evaluation.setAttemptNumber(attempt);
        evaluation.setTriggerType(trigger);
        evaluation.setRequestedBy(actor);
        evaluation.setStatus(JudgeStatus.QUEUED);
        evaluation.setCurrentOfficial(false);
        return evaluation;
    }

    private List<JudgeTestCaseEntity> allCases(JudgeTestCaseVersionEntity version) {
        List<JudgeTestCaseEntity> items = testCases.findByVersionIdOrderByOrdinalNumber(version.getId());
        if (items.isEmpty()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,
                "TEST_CASES_REQUIRED", "Active test-case version is empty");
        return items;
    }

    private AccountEntity activeAccount(UUID id) {
        AccountEntity account = accounts.findById(id)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED_ACCESS", "Account is unavailable"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw error(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "Account is not active");
        }
        return account;
    }

    private JudgeProblemEntity problem(UUID id) {
        return problems.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,
                "PROBLEM_NOT_FOUND", "Judge problem was not found"));
    }

    private SubmissionEntity submission(UUID id) {
        return submissions.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,
                "SUBMISSION_NOT_FOUND", "Submission was not found"));
    }

    private JudgeTestCaseVersionEntity activeVersion(UUID problemId) {
        return versions.findByProblemIdAndActiveTrue(problemId).orElseThrow(() -> error(HttpStatus.UNPROCESSABLE_ENTITY,
                "TEST_CASE_VERSION_REQUIRED", "Problem has no active test-case version"));
    }

    private static void requireLanguage(JudgeProblemEntity problem, JudgeLanguage language) {
        if (!problem.supports(language)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,
                "LANGUAGE_NOT_SUPPORTED", "Language is not enabled for this problem");
    }

    private static String starterCode(JudgeProblemEntity problem, JudgeLanguage language) {
        return language == JudgeLanguage.JAVA ? problem.getStarterCodeJava() : problem.getStarterCodePython();
    }

    private static JudgeExecutionPort.TestCase executionCase(JudgeTestCaseEntity value) {
        return new JudgeExecutionPort.TestCase(value.getOrdinalNumber(), value.getVisibility(),
                value.getInputData(), value.getExpectedOutput());
    }

    private static SubmissionResponse response(SubmissionEntity value, List<CaseResult> cases) {
        return new SubmissionResponse(value.getId(), value.getProblem().getId(), value.getSubmitter().getId(),
                value.getTestCaseVersion().getId(), value.getLanguage(), value.getStatus(),
                value.getExecutionTimeMs(), value.getMemoryUsedKb(), value.getCompilerOutput(), value.getErrorMessage(),
                value.getCompletedAt(), value.getCreatedAt(), List.copyOf(cases));
    }

    private static ProblemResponse problemResponse(JudgeProblemEntity value) {
        Set<JudgeLanguage> languages = new LinkedHashSet<>();
        for (String language : value.getEnabledLanguages().split(",")) languages.add(JudgeLanguage.valueOf(language));
        return new ProblemResponse(value.getId(), value.getOwner().getId(), value.getContextType(), value.getContextId(), value.getClassroomId(),
                value.getTitle(), value.getFormat(), value.getTimeLimitMs(), value.getMemoryLimitMb(),
                value.getFeedbackLevel(), Set.copyOf(languages), value.getStarterCodeJava(), value.getStarterCodePython(),
                value.isActive(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private static String languageCsv(Set<JudgeLanguage> languages) {
        if (languages == null || languages.isEmpty()) throw error(HttpStatus.BAD_REQUEST,
                "LANGUAGE_REQUIRED", "At least one language is required");
        return languages.stream().sorted().map(Enum::name).reduce((left, right) -> left + "," + right).orElseThrow();
    }

    private static String testCaseChecksum(List<TestCaseInput> items) {
        StringBuilder value = new StringBuilder();
        int ordinal = 1;
        for (TestCaseInput item : items) value.append(ordinal++).append('\u001f').append(item.visibility())
                .append('\u001f').append(item.input()).append('\u001f').append(item.expectedOutput())
                .append('\u001f').append(item.weight()).append('\u001e');
        return sha256(value.toString());
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String clean(String value) { return value == null ? null : value.strip(); }

    private static AppException error(HttpStatus status, String code, String message) {
        return new JudgeDomainException(status, code, message);
    }

    private static final class JudgeDomainException extends AppException {
        private JudgeDomainException(HttpStatus status, String code, String message) { super(message, status, code); }
    }
}
