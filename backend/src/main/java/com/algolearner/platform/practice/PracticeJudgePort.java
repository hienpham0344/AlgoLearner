package com.algolearner.platform.practice;

import java.util.List;
import java.util.UUID;

/**
 * Contract between Classroom Practice (4.8) and the Code Judge (4.9). The judge runs user code in the
 * isolated sandbox; 4.8 never executes user code itself. Implementations signal outage by throwing
 * {@link com.algolearner.platform.common.exception.JudgeServiceUnavailableException} (any other runtime
 * exception is treated the same way).
 */
public interface PracticeJudgePort {

    /** RUN: sample testcases or one custom input, immediate result, nothing persisted by 4.8. */
    RunResult run(RunRequest request);

    /** SUBMIT: judge every testcase of the current version and return only the aggregate outcome. */
    SubmitResult submit(SubmitRequest request);

    /** {@code expectedOutput} is null for a custom-input RUN: the judge only executes, it cannot compare. */
    record JudgeCase(int index, String input, String expectedOutput) { }

    record RunRequest(UUID practiceId, PracticeProblemType problemType, PracticeLanguage language, String sourceCode,
                      int timeLimitMs, int memoryLimitMb, List<JudgeCase> testcases) { }

    record CaseResult(int index, PracticeVerdict verdict, String stdout, String stderr, Integer runtimeMs) { }

    record RunResult(List<CaseResult> cases) { }

    record SubmitRequest(UUID practiceId, PracticeProblemType problemType, PracticeLanguage language, String sourceCode,
                         int timeLimitMs, int memoryLimitMb, int testcaseVersion, List<JudgeCase> testcases) { }

    /**
     * Aggregate only: must never carry hidden testcase input or expected output. {@code testsPassed} is the number of
     * testcases of the submitted version that passed (0..testcases.size(); equal to it when ACCEPTED). A result with
     * INTERNAL_ERROR, no verdict or an inconsistent count is treated as a judge failure, never stored.
     * {@code details} is stored, not exposed.
     */
    record SubmitResult(PracticeVerdict verdict, int testsPassed, Integer runtimeMs, Integer memoryKb, String details) { }
}
