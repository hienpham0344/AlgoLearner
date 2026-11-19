package com.algolearner.platform.judge;

import java.util.List;

import static com.algolearner.platform.judge.JudgeTypes.*;

/** The only boundary allowed to execute untrusted source code. Implementations must use an isolated provider. */
public interface JudgeExecutionPort {
    ExecutionResult execute(ExecutionRequest request);

    record ExecutionRequest(JudgeLanguage language, JudgeProblemFormat format, String sourceCode,
            String starterCode, int timeLimitMs, int memoryLimitMb, boolean official,
            List<TestCase> testCases) {
        public ExecutionRequest {
            testCases = List.copyOf(testCases);
        }
    }

    record TestCase(int ordinalNumber, TestCaseVisibility visibility, String input, String expectedOutput) {}
    record CaseResult(int ordinalNumber, JudgeStatus status, long executionTimeMs, long memoryUsedKb,
            String actualOutput, String stderrOutput) {}
    record ExecutionResult(JudgeStatus status, String stdout, String compilerOutput,
            long executionTimeMs, long memoryUsedKb, List<CaseResult> cases, String provider) {
        public ExecutionResult {
            cases = cases == null ? List.of() : List.copyOf(cases);
        }
    }
}
