package com.algolearner.platform.judge;

import static com.algolearner.platform.judge.JudgeGateway.*;
import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Docker command fixtures verify orchestration, not actual container isolation. */
class DockerSandboxLifecycleTest {
    private static final String IMAGE = "fixture/image@sha256:"+"a".repeat(64);
    @TempDir Path root;

    @Test
    void failedVolumeCreationStillRemovesSourceAndAttemptsVolumeCleanup() throws Exception {
        var commands = new Commands();
        commands.createFailure = new IOException("create failed");
        commands.removeFailure = new IOException("remove failed");
        assertThatThrownBy(() -> sandbox(commands).evaluate(UUID.randomUUID(),request()))
                .isSameAs(commands.createFailure).hasSuppressedException(commands.removeFailure);
        assertEmptyDirectory();
        assertThat(commands.controls).anySatisfy(command -> assertThat(command).containsSubsequence("volume","rm","-f"));
    }

    @Test
    void failedVolumeRemovalDoesNotPreventSourceCleanup() throws Exception {
        var commands = new Commands();
        commands.removeFailure = new IOException("remove failed");
        assertThatThrownBy(() -> sandbox(commands).evaluate(UUID.randomUUID(),request())).isSameAs(commands.removeFailure);
        assertEmptyDirectory();
    }

    @ParameterizedTest
    @CsvSource({"137,false,false,RUNTIME_ERROR", "137,true,false,MEMORY_LIMIT_EXCEEDED", "-1,false,true,TIME_LIMIT_EXCEEDED"})
    void verdictRequiresOomEvidenceRatherThanExitCodeAlone(int exit,boolean oom,boolean timeout,Verdict expected) throws Exception {
        var commands = new Commands();
        commands.result = new DockerSandbox.Execution(exit,"",false,timeout,oom);
        assertThat(sandbox(commands).evaluate(UUID.randomUUID(),request()).verdict()).isEqualTo(expected);
        assertEmptyDirectory();
    }

    @Test
    void infrastructureFailurePropagatesWithoutFabricatingLearnerResult() throws Exception {
        var commands = new Commands();
        commands.executionFailure = new IOException("inspection unavailable");
        assertThatThrownBy(() -> sandbox(commands).evaluate(UUID.randomUUID(),request())).isSameAs(commands.executionFailure);
        assertEmptyDirectory();
    }

    private DockerSandbox sandbox(Commands commands) throws IOException {
        return new DockerSandbox(IMAGE,IMAGE,root,commands);
    }

    private void assertEmptyDirectory() throws IOException {
        try (var entries = Files.list(root)) { assertThat(entries.toList()).isEmpty(); }
    }

    private CodeEvaluationRequest request() {
        return new CodeEvaluationRequest(UUID.randomUUID(),null,null,"print(input())",Language.PYTHON,
                List.of(new TestCaseSnapshot("input","output",true,false)),UUID.randomUUID().toString(),0,
                Feedback.PASS_FAIL,SubmissionScope.PUBLIC_PROBLEM,UUID.randomUUID(),UUID.randomUUID(),new Limits(10,2000,65536));
    }

    private static final class Commands implements DockerSandbox.DockerCommands {
        final List<List<String>> controls = new ArrayList<>();
        IOException createFailure,removeFailure,executionFailure;
        DockerSandbox.Execution result = new DockerSandbox.Execution(0,"output",false,false,false);
        @Override public void control(List<String> args) throws IOException {
            controls.add(List.copyOf(args));
            if (args.get(2).equals("create") && createFailure != null) throw createFailure;
            if (args.get(2).equals("rm") && removeFailure != null) throw removeFailure;
        }
        @Override public DockerSandbox.Execution execute(List<String> args,String input,int timeoutMillis,int limit) throws IOException {
            assertThat(args).containsSubsequence("--network","none");
            assertThat(args).doesNotContain("--rm");
            if (executionFailure != null) throw executionFailure;
            return result;
        }
    }
}
