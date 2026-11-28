package com.algolearner.platform.judge;

import java.util.List;
import java.util.UUID;

/** Trusted server port. Calling modules must authorize owner/question/attempt before constructing a request. */
public interface JudgeGateway {
    enum SubmissionScope { CLASS_ASSIGNMENT, CLASS_PRACTICE, PUBLIC_PROBLEM }
    record Limits(int compileSeconds, int runtimeMillis, int outputBytes) { }
    enum Language { JAVA, PYTHON }
    enum Feedback { PASS_FAIL, TESTCASE_STATUS, FULL_DETAILS }
    enum Verdict { QUEUED, RUNNING, ACCEPTED, WRONG_ANSWER, COMPILE_ERROR, RUNTIME_ERROR, MEMORY_LIMIT_EXCEEDED, TIME_LIMIT_EXCEEDED, OUTPUT_LIMIT_EXCEEDED, INFRASTRUCTURE_ERROR }
    record TestCaseSnapshot(String input, String expectedOutput, boolean sample, boolean allowDetails) { }
    record CodeEvaluationRequest(UUID ownerId, UUID questionId, UUID attemptId, String source,
            Language language, List<TestCaseSnapshot> testCases, String requestKey, long generation, Feedback feedback,
            SubmissionScope scope, UUID resourceId, UUID revisionId, Limits limits) {
        public CodeEvaluationRequest { testCases = List.copyOf(testCases); }
    }
    record CaseResult(int index, Verdict verdict, String input, String expectedOutput, String actualOutput) { }
    record EvaluationResult(UUID jobId, long generation, Verdict verdict, int passed, int total, List<CaseResult> cases,
                            Long runtimeMs, Long memoryUsedKb) {
        public EvaluationResult(UUID jobId,long generation,Verdict verdict,int passed,int total,List<CaseResult> cases) {
            this(jobId,generation,verdict,passed,total,cases,null,null);
        }
        public EvaluationResult { cases = List.copyOf(cases); }
    }
    record JobReference(UUID jobId, UUID ownerId, UUID attemptId, UUID questionId, SubmissionScope scope,
                        UUID resourceId, UUID revisionId, boolean run, long generation) { }
    UUID submit(CodeEvaluationRequest request);
    /** Trusted rejudge port; the caller authorizes the selected immutable testcase revision. */
    default UUID rejudge(CodeEvaluationRequest request) {
        throw new UnsupportedOperationException("Revision-aware rejudge is not available");
    }
    UUID run(CodeEvaluationRequest request);
    EvaluationResult result(UUID requesterId, UUID jobId);
    /** Trusted metadata port for a calling module to validate the parent resource before exposing a result. */
    default JobReference jobReference(UUID ownerId, UUID jobId) {
        throw new UnsupportedOperationException("Judge job metadata is not available");
    }
    /** Trusted grading port: per-case verdicts only, never testcase IO or learner output. */
    default EvaluationResult scoringResult(UUID ownerId, UUID jobId) {
        throw new UnsupportedOperationException("Per-case grading is not available");
    }
}
