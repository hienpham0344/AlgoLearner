package com.algolearner.platform.judge;

import java.util.ArrayList;
import java.util.List;

import static com.algolearner.platform.judge.JudgeExecutionPort.*;
import static com.algolearner.platform.judge.JudgeTypes.*;

/**
 * Mock execution provider for local development, CI and testing environments
 * without requiring a live Docker daemon.
 */
public class MockJudgeExecutionPort implements JudgeExecutionPort {

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        String code = request.sourceCode() == null ? "" : request.sourceCode();

        if (code.contains("// COMPILE_ERROR") || code.contains("# COMPILE_ERROR")) {
            return new ExecutionResult(JudgeStatus.COMPILATION_ERROR, "", "Compilation failed: syntax error",
                    10, 32, List.of(), "mock");
        }
        if (code.contains("// RUNTIME_ERROR") || code.contains("# RUNTIME_ERROR")) {
            return new ExecutionResult(JudgeStatus.RUNTIME_ERROR, "", "Exception in thread \"main\" java.lang.RuntimeException: Mock runtime error",
                    15, 48, List.of(), "mock");
        }
        if (code.contains("// TLE") || code.contains("# TLE")) {
            return new ExecutionResult(JudgeStatus.TIME_LIMIT_EXCEEDED, "", "Process timed out",
                    request.timeLimitMs() + 100, 64, List.of(), "mock");
        }
        if (code.contains("// MLE") || code.contains("# MLE")) {
            return new ExecutionResult(JudgeStatus.MEMORY_LIMIT_EXCEEDED, "", "Out of memory",
                    50, (request.memoryLimitMb() + 10) * 1024L, List.of(), "mock");
        }

        boolean forceWrong = code.contains("// WRONG_ANSWER") || code.contains("# WRONG_ANSWER");
        List<CaseResult> caseResults = new ArrayList<>();
        JudgeStatus aggregateStatus = JudgeStatus.ACCEPTED;
        long totalTime = 0;
        long maxMem = 32;

        for (TestCase tc : request.testCases()) {
            JudgeStatus caseStatus = forceWrong ? JudgeStatus.WRONG_ANSWER : JudgeStatus.ACCEPTED;
            if (caseStatus != JudgeStatus.ACCEPTED && aggregateStatus == JudgeStatus.ACCEPTED) {
                aggregateStatus = caseStatus;
            }
            long caseTime = 25;
            long caseMem = 64;
            totalTime += caseTime;
            maxMem = Math.max(maxMem, caseMem);
            String output = forceWrong ? "mock_wrong_output\n"
                    : (tc.expectedOutput() != null ? tc.expectedOutput()
                    : (tc.input() != null ? tc.input() + "\n" : "mock_output\n"));
            caseResults.add(new CaseResult(tc.ordinalNumber(), caseStatus, caseTime, caseMem, output, null));
        }

        String stdout = caseResults.isEmpty() ? "mock_output\n" : caseResults.getFirst().actualOutput();
        return new ExecutionResult(aggregateStatus, stdout, null, totalTime, maxMem, caseResults, "mock");
    }
}

