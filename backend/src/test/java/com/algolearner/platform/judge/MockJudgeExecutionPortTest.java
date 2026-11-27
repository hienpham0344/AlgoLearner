package com.algolearner.platform.judge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.algolearner.platform.judge.JudgeExecutionPort.*;
import static com.algolearner.platform.judge.JudgeTypes.*;
import static org.assertj.core.api.Assertions.assertThat;

class MockJudgeExecutionPortTest {
    private final MockJudgeExecutionPort port = new MockJudgeExecutionPort();

    @Test
    void compileErrorTriggeredByKeyword() {
        ExecutionRequest request = new ExecutionRequest(JudgeLanguage.JAVA, JudgeProblemFormat.STANDARD_IO,
                "// COMPILE_ERROR\nclass Main {}", "", 2000, 256, true, List.of());
        ExecutionResult result = port.execute(request);

        assertThat(result.status()).isEqualTo(JudgeStatus.COMPILATION_ERROR);
        assertThat(result.compilerOutput()).contains("Compilation failed");
    }

    @Test
    void runtimeErrorTriggeredByKeyword() {
        ExecutionRequest request = new ExecutionRequest(JudgeLanguage.PYTHON, JudgeProblemFormat.STANDARD_IO,
                "# RUNTIME_ERROR\nprint(1/0)", "", 2000, 256, true, List.of());
        ExecutionResult result = port.execute(request);

        assertThat(result.status()).isEqualTo(JudgeStatus.RUNTIME_ERROR);
    }

    @Test
    void timeLimitExceededTriggeredByKeyword() {
        ExecutionRequest request = new ExecutionRequest(JudgeLanguage.JAVA, JudgeProblemFormat.STANDARD_IO,
                "// TLE\nwhile(true);", "", 2000, 256, true, List.of());
        ExecutionResult result = port.execute(request);

        assertThat(result.status()).isEqualTo(JudgeStatus.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void memoryLimitExceededTriggeredByKeyword() {
        ExecutionRequest request = new ExecutionRequest(JudgeLanguage.JAVA, JudgeProblemFormat.STANDARD_IO,
                "// MLE\nbyte[] b = new byte[100000000];", "", 2000, 256, true, List.of());
        ExecutionResult result = port.execute(request);

        assertThat(result.status()).isEqualTo(JudgeStatus.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    void wrongAnswerTriggeredByKeyword() {
        TestCase tc1 = new TestCase(1, TestCaseVisibility.SAMPLE, "1 2", "3");
        ExecutionRequest request = new ExecutionRequest(JudgeLanguage.JAVA, JudgeProblemFormat.STANDARD_IO,
                "// WRONG_ANSWER\nSystem.out.println(4);", "", 2000, 256, true, List.of(tc1));
        ExecutionResult result = port.execute(request);

        assertThat(result.status()).isEqualTo(JudgeStatus.WRONG_ANSWER);
        assertThat(result.cases()).hasSize(1);
        assertThat(result.cases().getFirst().status()).isEqualTo(JudgeStatus.WRONG_ANSWER);
    }

    @Test
    void normalAcceptedExecutionProducesExpectedOutputs() {
        TestCase tc1 = new TestCase(1, TestCaseVisibility.SAMPLE, "1 2", "3\n");
        TestCase tc2 = new TestCase(2, TestCaseVisibility.HIDDEN, "4 5", "9\n");
        ExecutionRequest request = new ExecutionRequest(JudgeLanguage.JAVA, JudgeProblemFormat.STANDARD_IO,
                "import java.util.*; class Main { public static void main(String[] args) { Scanner s = new Scanner(System.in); System.out.println(s.nextInt() + s.nextInt()); } }",
                "", 2000, 256, true, List.of(tc1, tc2));

        ExecutionResult result = port.execute(request);

        assertThat(result.status()).isEqualTo(JudgeStatus.ACCEPTED);
        assertThat(result.cases()).hasSize(2);
        assertThat(result.cases()).allMatch(c -> c.status() == JudgeStatus.ACCEPTED);
        assertThat(result.cases().get(0).actualOutput()).isEqualTo("3\n");
        assertThat(result.cases().get(1).actualOutput()).isEqualTo("9\n");
    }
}

