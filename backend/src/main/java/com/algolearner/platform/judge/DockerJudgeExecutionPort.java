package com.algolearner.platform.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.algolearner.platform.judge.JudgeExecutionPort.*;
import static com.algolearner.platform.judge.JudgeTypes.*;

/**
 * Executes untrusted Java and Python source code inside isolated, resource-constrained
 * Docker containers in strict compliance with AlgoLearner SRS 4.9 and Sandbox Security Rules:
 *  - Network isolated (--network none)
 *  - Memory constrained (--memory Xm)
 *  - CPU throttled (--cpus 1.0)
 *  - PID limited (--pids-limit 32)
 *  - Capabilities dropped (--cap-drop=ALL)
 *  - No privilege escalation (--security-opt=no-new-privileges:true)
 *  - Ephemeral containers (--rm)
 */
public class DockerJudgeExecutionPort implements JudgeExecutionPort {
    private static final Logger LOG = LoggerFactory.getLogger(DockerJudgeExecutionPort.class);
    private static final int TIMEOUT_GRACE_MS = 1500;

    private final String imageJava;
    private final String imagePython;
    private final int commandTimeoutSeconds;

    public DockerJudgeExecutionPort(String imageJava, String imagePython, int commandTimeoutSeconds) {
        this.imageJava = imageJava != null && !imageJava.isBlank() ? imageJava : "eclipse-temurin:21-alpine";
        this.imagePython = imagePython != null && !imagePython.isBlank() ? imagePython : "python:3.11-alpine";
        this.commandTimeoutSeconds = commandTimeoutSeconds > 0 ? commandTimeoutSeconds : 15;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        verifyDockerAvailable();

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("algolearner-sandbox-");
            String volumePath = tempDir.toAbsolutePath().toString().replace('\\', '/');

            if (request.language() == JudgeLanguage.JAVA) {
                return executeJava(request, tempDir, volumePath);
            } else if (request.language() == JudgeLanguage.PYTHON) {
                return executePython(request, tempDir, volumePath);
            } else {
                throw new JudgeProviderException("Unsupported language: " + request.language());
            }
        } catch (IOException e) {
            throw new JudgeProviderException("Failed to prepare sandbox directory: " + e.getMessage(), e);
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir.toFile());
            }
        }
    }

    private ExecutionResult executeJava(ExecutionRequest request, Path tempDir, String volumePath) throws IOException {
        Files.writeString(tempDir.resolve("Main.java"), request.sourceCode(), StandardCharsets.UTF_8);

        // Step 1: Compile Main.java
        List<String> compileCommand = List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--memory", "256m",
                "--cpus", "1.0",
                "--pids-limit", "32",
                "--cap-drop=ALL",
                "--security-opt", "no-new-privileges:true",
                "-v", volumePath + ":/sandbox",
                "-w", "/sandbox",
                imageJava,
                "javac", "Main.java"
        );

        ProcessOutput compileOut = runProcess(compileCommand, null, commandTimeoutSeconds * 1000L);
        if (compileOut.timedOut) {
            return new ExecutionResult(JudgeStatus.TIME_LIMIT_EXCEEDED, "", "Compilation timed out",
                    commandTimeoutSeconds * 1000L, 0, List.of(), "docker");
        }
        if (compileOut.exitCode != 0) {
            String compilerError = (compileOut.stderr + "\n" + compileOut.stdout).trim();
            return new ExecutionResult(JudgeStatus.COMPILATION_ERROR, "", compilerError,
                    compileOut.durationMs, 0, List.of(), "docker");
        }

        // Step 2: Execute test cases against compiled Main.class (read-only volume)
        return runTestCases(request, volumePath, imageJava, List.of(
                "java", "-Xmx" + request.memoryLimitMb() + "m", "-XX:+UseSerialGC", "Main"
        ));
    }

    private ExecutionResult executePython(ExecutionRequest request, Path tempDir, String volumePath) throws IOException {
        Files.writeString(tempDir.resolve("solution.py"), request.sourceCode(), StandardCharsets.UTF_8);

        // Step 1: Syntax compile check
        List<String> compileCommand = List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--memory", "128m",
                "--cpus", "1.0",
                "--pids-limit", "32",
                "--cap-drop=ALL",
                "--security-opt", "no-new-privileges:true",
                "-v", volumePath + ":/sandbox",
                "-w", "/sandbox",
                imagePython,
                "python3", "-m", "py_compile", "solution.py"
        );

        ProcessOutput compileOut = runProcess(compileCommand, null, commandTimeoutSeconds * 1000L);
        if (compileOut.timedOut) {
            return new ExecutionResult(JudgeStatus.TIME_LIMIT_EXCEEDED, "", "Syntax check timed out",
                    commandTimeoutSeconds * 1000L, 0, List.of(), "docker");
        }
        if (compileOut.exitCode != 0) {
            String compilerError = (compileOut.stderr + "\n" + compileOut.stdout).trim();
            return new ExecutionResult(JudgeStatus.COMPILATION_ERROR, "", compilerError,
                    compileOut.durationMs, 0, List.of(), "docker");
        }

        // Step 2: Execute test cases (read-only volume)
        return runTestCases(request, volumePath, imagePython, List.of("python3", "solution.py"));
    }

    private ExecutionResult runTestCases(ExecutionRequest request, String volumePath, String image,
            List<String> runnerArgs) {
        List<CaseResult> caseResults = new ArrayList<>();
        JudgeStatus aggregateStatus = JudgeStatus.ACCEPTED;
        long totalExecutionTime = 0;
        long maxMemoryUsed = 0;
        String firstStdout = "";

        for (TestCase tc : request.testCases()) {
            List<String> runCommand = new ArrayList<>();
            runCommand.addAll(List.of(
                    "docker", "run", "--rm", "-i",
                    "--network", "none",
                    "--memory", request.memoryLimitMb() + "m",
                    "--cpus", "1.0",
                    "--pids-limit", "32",
                    "--cap-drop=ALL",
                    "--security-opt", "no-new-privileges:true",
                    "-v", volumePath + ":/sandbox:ro",
                    "-w", "/sandbox",
                    image
            ));
            runCommand.addAll(runnerArgs);

            long timeoutMs = request.timeLimitMs() + TIMEOUT_GRACE_MS;
            ProcessOutput out = runProcess(runCommand, tc.input(), timeoutMs);

            JudgeStatus caseStatus;
            if (out.timedOut || out.durationMs > request.timeLimitMs()) {
                caseStatus = JudgeStatus.TIME_LIMIT_EXCEEDED;
            } else if (out.exitCode == 137) { // Linux container OOM killed
                caseStatus = JudgeStatus.MEMORY_LIMIT_EXCEEDED;
            } else if (out.exitCode != 0) {
                caseStatus = JudgeStatus.RUNTIME_ERROR;
            } else if (tc.expectedOutput() != null && !normalizeOutput(out.stdout).equals(normalizeOutput(tc.expectedOutput()))) {
                caseStatus = JudgeStatus.WRONG_ANSWER;
            } else {
                caseStatus = JudgeStatus.ACCEPTED;
            }

            if (caseResults.isEmpty()) {
                firstStdout = out.stdout;
            }
            if (caseStatus != JudgeStatus.ACCEPTED && aggregateStatus == JudgeStatus.ACCEPTED) {
                aggregateStatus = caseStatus;
            }

            totalExecutionTime += out.durationMs;
            maxMemoryUsed = Math.max(maxMemoryUsed, 64); // approximated container resident memory in KB
            caseResults.add(new CaseResult(tc.ordinalNumber(), caseStatus, out.durationMs,
                    maxMemoryUsed, out.stdout, out.stderr));
        }

        return new ExecutionResult(aggregateStatus, firstStdout, null,
                totalExecutionTime, maxMemoryUsed, caseResults, "docker");
    }

    private void verifyDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info").start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new JudgeProviderException("Docker daemon check timed out");
            }
            if (process.exitValue() != 0) {
                throw new JudgeProviderException("Docker daemon is not running or unreachable");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JudgeProviderException("Docker check interrupted", e);
        } catch (IOException e) {
            throw new JudgeProviderException("Docker CLI is not available on host system: " + e.getMessage(), e);
        }
    }

    private ProcessOutput runProcess(List<String> command, String stdinInput, long timeoutMs) {
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            Process process = builder.start();

            if (stdinInput != null && !stdinInput.isEmpty()) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(stdinInput.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException ignored) {}
            } else {
                process.getOutputStream().close();
            }

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long duration = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                return new ProcessOutput(-1, "", "Process timed out", duration, true);
            }

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            return new ProcessOutput(process.exitValue(), stdout, stderr, duration, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessOutput(-1, "", "Interrupted", System.currentTimeMillis() - start, true);
        } catch (IOException e) {
            LOG.error("Failed to execute process: {}", command, e);
            throw new JudgeProviderException("Failed to run sandbox container: " + e.getMessage(), e);
        }
    }

    private static String readStream(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static String normalizeOutput(String raw) {
        if (raw == null) return "";
        return raw.replace("\r\n", "\n").stripTrailing();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    private record ProcessOutput(int exitCode, String stdout, String stderr, long durationMs, boolean timedOut) {}
}
