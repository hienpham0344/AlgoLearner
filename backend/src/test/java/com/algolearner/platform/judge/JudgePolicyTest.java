package com.algolearner.platform.judge;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static com.algolearner.platform.judge.JudgeGateway.*;

class JudgePolicyTest {
    @Test void tokenComparatorIsWhitespaceNormalizedButCaseSensitive() {
        assertThat(JudgePolicy.matches("  a\t b\r\n", "a b")).isTrue();
        assertThat(JudgePolicy.matches("A", "a")).isFalse();
        assertThat(JudgePolicy.matches("1 2", "12")).isFalse();
    }
    @Test void hiddenDetailsRequireExplicitPermissionEvenFullFeedback() {
        var hidden = new TestCaseSnapshot("secret", "answer", false, false);
        var result = JudgePolicy.visibleCase(0, hidden, Verdict.WRONG_ANSWER, "leak", Feedback.FULL_DETAILS);
        assertThat(result.input()).isNull(); assertThat(result.actualOutput()).isNull(); assertThat(result.expectedOutput()).isNull();
        assertThat(JudgePolicy.visibleCase(0, hidden, Verdict.WRONG_ANSWER, "leak", Feedback.PASS_FAIL)).isNull();
    }
    @Test void sourceAndTestcaseLimitsAreCheckedBeforePersistence() {
        var request = new CodeEvaluationRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "x".repeat(65537), Language.JAVA, List.of(new TestCaseSnapshot("", "", true, false)), "key", 1, Feedback.PASS_FAIL, SubmissionScope.CLASS_ASSIGNMENT, UUID.randomUUID(), UUID.randomUUID(), new Limits(10,2000,65536));
        assertThatThrownBy(() -> JudgePolicy.validate(request)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void workerImagesMustBeImmutableDigestReferences() {
        assertThatThrownBy(() -> DockerSandbox.requirePinnedImage("python:latest")).isInstanceOf(IllegalArgumentException.class);
        assertThat(DockerSandbox.requirePinnedImage("registry/python@sha256:" + "a".repeat(64))).contains("@sha256:");
    }
    @Test void testcaseInputRequiresInteractiveStdinWithoutATerminal() {
        assertThat(DockerSandbox.baseCommand("image@sha256:"+"a".repeat(64),"case"))
                .contains("--interactive").doesNotContain("--tty","-t");
    }
    @Test void runtimeFlagsAlwaysIsolateAndApiHasNoWorkerBean() {
        assertThat(DockerSandbox.baseCommand("unused", "job")).contains("--network", "none", "--memory", "256m", "--cpus", "1.0",
                "--pids-limit", "32", "--cap-drop=ALL", "--read-only", "65534:65534", "no-new-privileges");
        assertThat(JudgeWorkerMain.class.getAnnotations()).isEmpty();
        assertThat(JdbcJudgeGateway.class.getDeclaredMethods()).noneMatch(m -> m.getReturnType() == Process.class);
    }
}
