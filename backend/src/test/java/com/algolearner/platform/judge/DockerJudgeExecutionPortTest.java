package com.algolearner.platform.judge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.algolearner.platform.judge.JudgeExecutionPort.*;
import static com.algolearner.platform.judge.JudgeTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerJudgeExecutionPortTest {

    @Test
    void unsupportedLanguageThrowsJudgeProviderException() {
        DockerJudgeExecutionPort port = new DockerJudgeExecutionPort("custom-java", "custom-python", 5);
        ExecutionRequest request = new ExecutionRequest(null, JudgeProblemFormat.STANDARD_IO,
                "code", "", 2000, 256, false, List.of());

        assertThatThrownBy(() -> port.execute(request))
                .isInstanceOf(JudgeProviderException.class);
    }

    @Test
    void uncontactableDockerDaemonOrMissingImageThrowsJudgeProviderException() {
        DockerJudgeExecutionPort port = new DockerJudgeExecutionPort("non-existent-img:999", "non-existent-img:999", 5);
        ExecutionRequest request = new ExecutionRequest(JudgeLanguage.JAVA, JudgeProblemFormat.STANDARD_IO,
                "class Main {}", "", 1000, 128, false, List.of());

        assertThatThrownBy(() -> port.execute(request))
                .isInstanceOf(JudgeProviderException.class);
    }

    @Test
    void runProcessDrainsLargeStdinAndStdoutConcurrentlyWithoutDeadlockAndTruncatesOutput() {
        DockerJudgeExecutionPort port = new DockerJudgeExecutionPort("java", "python", 5);
        String largeInput = "line\n".repeat(20_000);
        List<String> cmd = isWindows()
                ? List.of("cmd", "/c", "findstr", "line")
                : List.of("sh", "-c", "cat");

        DockerJudgeExecutionPort.ProcessOutput out = port.runProcess(cmd, "test-container", largeInput, 10_000);

        assertThat(out.timedOut()).isFalse();
        assertThat(out.exitCode()).isZero();
        assertThat(out.stdout()).isNotEmpty();
    }

    @Test
    void runProcessTruncatesOversizedOutput() {
        DockerJudgeExecutionPort port = new DockerJudgeExecutionPort("java", "python", 5);
        // Generate > 300KB output directly without reading stdin
        List<String> cmd = isWindows()
                ? List.of("powershell", "-NoProfile", "-Command", "Write-Output ('X' * 300000)")
                : List.of("sh", "-c", "head -c 300000 /dev/zero | tr '\\0' 'X'");

        DockerJudgeExecutionPort.ProcessOutput out = port.runProcess(cmd, "test-container", null, 10_000);

        assertThat(out.timedOut()).isFalse();
        assertThat(out.stdout()).contains("...[output truncated]");
        assertThat(out.stdout().length()).isLessThan(270 * 1024);
    }

    @Test
    void runProcessCleansUpDockerContainerOnTimeout() {
        AtomicReference<String> removedContainer = new AtomicReference<>();
        DockerJudgeExecutionPort port = new DockerJudgeExecutionPort("java", "python", 5) {
            @Override
            protected void removeContainer(String containerName) {
                removedContainer.set(containerName);
            }
        };

        List<String> sleepCmd = isWindows()
                ? List.of("powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 5")
                : List.of("sleep", "5");

        DockerJudgeExecutionPort.ProcessOutput out = port.runProcess(sleepCmd, "algolearner-judge-timeout-test", null, 150);

        assertThat(out.timedOut()).isTrue();
        assertThat(removedContainer.get()).isEqualTo("algolearner-judge-timeout-test");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
