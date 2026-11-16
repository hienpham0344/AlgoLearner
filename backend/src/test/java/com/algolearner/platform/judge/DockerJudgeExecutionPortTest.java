package com.algolearner.platform.judge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.algolearner.platform.judge.JudgeExecutionPort.*;
import static com.algolearner.platform.judge.JudgeTypes.*;
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
    void uncontactableDockerDaemonThrowsJudgeProviderException() {
        // Points to non-existent images or when docker daemon is not active
        DockerJudgeExecutionPort port = new DockerJudgeExecutionPort("non-existent-img:999", "non-existent-img:999", 1);
        ExecutionRequest request = new ExecutionRequest(JudgeLanguage.JAVA, JudgeProblemFormat.STANDARD_IO,
                "class Main {}", "", 1000, 128, false, List.of());

        assertThatThrownBy(() -> port.execute(request))
                .isInstanceOf(JudgeProviderException.class);
    }
}

