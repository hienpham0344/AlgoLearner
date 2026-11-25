package com.algolearner.platform.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *  - Ephemeral containers (--rm) with forced cleanup on timeout/interruption
 *  - Concurrent bounded stdout/stderr draining and end-to-end deadline
 */
public class DockerJudgeExecutionPort implements JudgeExecutionPort {
    private static final Logger LOG = LoggerFactory.getLogger(DockerJudgeExecutionPort.class);
    private static final int TIMEOUT_GRACE_MS = 1500;
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;
    private static final int DOCKER_INFRASTRUCTURE_EXIT_CODE = 125;
    private static final String TRUNCATED_SUFFIX = "\n...[output truncated]";

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

        String containerName = newContainerName();
        List<String> compileCommand = List.of(
                "docker", "run", "--rm",
                "--name", containerName,
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

        ProcessOutput compileOut = runProcess(compileCommand, containerName, null, commandTimeoutSeconds * 1000L);
        checkDockerInfrastructureError(compileOut);
        if (compileOut.timedOut) {
            return new ExecutionResult(JudgeStatus.TIME_LIMIT_EXCEEDED, "", "Compilation timed out",
                    commandTimeoutSeconds * 1000L, null, List.of(), "docker");
        }
        if (compileOut.exitCode != 0) {
            String compilerError = (compileOut.stderr + "\n" + compileOut.stdout).trim();
            return new ExecutionResult(JudgeStatus.COMPILATION_ERROR, "", compilerError,
                    compileOut.durationMs, null, List.of(), "docker");
        }

        return runTestCases(request, volumePath, imageJava, List.of(
                "java", "-Xmx" + request.memoryLimitMb() + "m", "-XX:+UseSerialGC", "Main"
        ));
    }

    private ExecutionResult executePython(ExecutionRequest request, Path tempDir, String volumePath) throws IOException {
        Files.writeString(tempDir.resolve("solution.py"), request.sourceCode(), StandardCharsets.UTF_8);

        String containerName = newContainerName();
        List<String> compileCommand = List.of(
                "docker", "run", "--rm",
                "--name", containerName,
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

        ProcessOutput compileOut = runProcess(compileCommand, containerName, null, commandTimeoutSeconds * 1000L);
        checkDockerInfrastructureError(compileOut);
        if (compileOut.timedOut) {
            return new ExecutionResult(JudgeStatus.TIME_LIMIT_EXCEEDED, "", "Syntax check timed out",
                    commandTimeoutSeconds * 1000L, null, List.of(), "docker");
        }
        if (compileOut.exitCode != 0) {
            String compilerError = (compileOut.stderr + "\n" + compileOut.stdout).trim();
            return new ExecutionResult(JudgeStatus.COMPILATION_ERROR, "", compilerError,
                    compileOut.durationMs, null, List.of(), "docker");
        }

        return runTestCases(request, volumePath, imagePython, List.of("python3", "solution.py"));
    }

    private ExecutionResult runTestCases(ExecutionRequest request, String volumePath, String image,
            List<String> runnerArgs) {
        List<CaseResult> caseResults = new ArrayList<>();
        JudgeStatus aggregateStatus = JudgeStatus.ACCEPTED;
        long totalExecutionTime = 0;
        String firstStdout = "";

        for (TestCase tc : request.testCases()) {
            String containerName = newContainerName();
            List<String> runCommand = new ArrayList<>(List.of(
                    "docker", "run", "--rm", "-i",
                    "--name", containerName,
                    "--network", "none",
                    "--memory", request.memoryLimitMb() + "m",
                    "--cpus", "1.0",
                    "--pids-limit", "32",
                    "--cap-drop=ALL",
                    "--security-opt", "no-new-privileges:true",
                    "--read-only",
                    "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=32m",
                    "-v", volumePath + ":/sandbox:ro",
                    "-w", "/sandbox",
                    image
            ));
            runCommand.addAll(runnerArgs);

            long timeoutMs = request.timeLimitMs() + TIMEOUT_GRACE_MS;
            ProcessOutput out = runProcess(runCommand, containerName, tc.input(), timeoutMs);
            checkDockerInfrastructureError(out);

            JudgeStatus caseStatus;
            if (out.timedOut || out.durationMs > request.timeLimitMs()) {
                caseStatus = JudgeStatus.TIME_LIMIT_EXCEEDED;
            } else if (out.exitCode == 137) {
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
            // Real per-container memory telemetry is not available via synchronous `docker run`; report null (unknown).
            caseResults.add(new CaseResult(tc.ordinalNumber(), caseStatus, out.durationMs,
                    null, out.stdout, out.stderr));
        }

        return new ExecutionResult(aggregateStatus, firstStdout, null,
                totalExecutionTime, null, caseResults, "docker");
    }

    private void verifyDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new JudgeProviderException("Docker daemon check timed out");
                }
                if (process.exitValue() != 0) {
                    throw new JudgeProviderException("Docker daemon is not running or unreachable");
                }
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JudgeProviderException("Docker check interrupted", e);
        } catch (IOException e) {
            throw new JudgeProviderException("Docker CLI is not available on host system: " + e.getMessage(), e);
        }
    }

    /**
     * Runs a command with concurrent stdin writing, concurrent bounded stdout/stderr draining,
     * an end-to-end deadline covering stdin + execution, and forced Docker container cleanup on timeout/interrupt.
     */
    ProcessOutput runProcess(List<String> command, String containerName, String stdinInput, long timeoutMs) {
        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(Math.max(1L, timeoutMs));
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            LOG.error("Failed to start sandbox process: {}", command, e);
            throw new JudgeProviderException("Failed to run sandbox container: " + e.getMessage(), e);
        }

        try (ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> stdinFuture = ioExecutor.submit(() -> writeStdin(process.getOutputStream(), stdinInput));
            Future<String> stdoutFuture = ioExecutor.submit(() -> drainBounded(process.getInputStream(), MAX_OUTPUT_BYTES));
            Future<String> stderrFuture = ioExecutor.submit(() -> drainBounded(process.getErrorStream(), MAX_OUTPUT_BYTES));

            long remainingMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
            boolean finished = process.waitFor(remainingMs, TimeUnit.MILLISECONDS);
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            if (!finished) {
                cleanupProcessAndContainer(process, containerName);
                cancelAndAwait(stdinFuture);
                String stdout = awaitStreamResult(stdoutFuture);
                String stderr = awaitStreamResult(stderrFuture);
                return new ProcessOutput(-1, stdout, stderr.isEmpty() ? "Process timed out" : stderr, durationMs, true);
            }

            // Ensure stdin writer doesn't linger if process exited early
            cancelAndAwait(stdinFuture);
            String stdout = awaitStreamResult(stdoutFuture);
            String stderr = awaitStreamResult(stderrFuture);
            return new ProcessOutput(process.exitValue(), stdout, stderr, durationMs, false);
        } catch (InterruptedException e) {
            cleanupProcessAndContainer(process, containerName);
            Thread.currentThread().interrupt();
            throw new JudgeProviderException("Sandbox execution interrupted", e);
        } finally {
            if (process.isAlive()) {
                cleanupProcessAndContainer(process, containerName);
            }
        }
    }

    protected void removeContainer(String containerName) {
        if (containerName == null || containerName.isBlank()) return;
        try {
            Process rm = new ProcessBuilder("docker", "rm", "-f", containerName)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                if (!rm.waitFor(5, TimeUnit.SECONDS)) {
                    rm.destroyForcibly();
                }
            } finally {
                if (rm.isAlive()) {
                    rm.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOG.debug("Ignoring container cleanup failure for {}: {}", containerName, e.getMessage());
        }
    }

    private void cleanupProcessAndContainer(Process process, String containerName) {
        process.destroyForcibly();
        removeContainer(containerName);
    }

    private static void writeStdin(OutputStream os, String stdinInput) {
        try (os) {
            if (stdinInput != null && !stdinInput.isEmpty()) {
                os.write(stdinInput.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (IOException ignored) {
            // Child process may close stdin early (e.g. compilation error or early exit).
        }
    }

    private static String drainBounded(InputStream is, int maxBytes) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(4096, maxBytes));
        AtomicBoolean truncated = new AtomicBoolean(false);
        byte[] chunk = new byte[4096];
        try (is) {
            int read;
            while ((read = is.read(chunk)) != -1) {
                int remaining = maxBytes - buffer.size();
                if (remaining > 0) {
                    int toWrite = Math.min(read, remaining);
                    buffer.write(chunk, 0, toWrite);
                    if (toWrite < read) {
                        truncated.set(true);
                    }
                } else {
                    truncated.set(true);
                }
            }
        } catch (IOException ignored) {
            // Stream closed when container/process terminates.
        }
        String text = buffer.toString(StandardCharsets.UTF_8);
        return truncated.get() ? text + TRUNCATED_SUFFIX : text;
    }

    private static void cancelAndAwait(Future<?> future) {
        future.cancel(true);
        try {
            future.get(500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Best-effort wait for writer cancellation.
        }
    }

    private static String awaitStreamResult(Future<String> future) {
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) {
            future.cancel(true);
            return "";
        }
    }

    private static void checkDockerInfrastructureError(ProcessOutput out) {
        if (out.timedOut) return;
        if (out.exitCode == DOCKER_INFRASTRUCTURE_EXIT_CODE || isDockerInfrastructureError(out.stderr)) {
            throw new JudgeProviderException("Docker sandbox infrastructure failure: " + out.stderr.trim());
        }
    }

    private static boolean isDockerInfrastructureError(String stderr) {
        if (stderr == null || stderr.isBlank()) return false;
        String lower = stderr.toLowerCase(Locale.ROOT);
        return lower.contains("failed to connect to the docker api")
                || lower.contains("cannot connect to the docker daemon")
                || lower.contains("error during connect")
                || lower.contains("daemon is not running")
                || lower.contains("unable to find image")
                || lower.contains("error response from daemon")
                || lower.contains("pull access denied");
    }

    private static String newContainerName() {
        return "algolearner-judge-" + UUID.randomUUID();
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

    record ProcessOutput(int exitCode, String stdout, String stderr, long durationMs, boolean timedOut) {}
}
