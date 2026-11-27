package com.algolearner.platform.judge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.algolearner.platform.judge.JudgeTypes.*;

public final class JudgeDtos {
    private JudgeDtos() {}

    public record RunRequest(
            @NotNull UUID problemId,
            @NotNull JudgeLanguage language,
            @NotBlank @Size(max = 100_000) String sourceCode,
            @Size(max = 100_000) String customInput) {}

    public record SubmitRequest(
            @NotNull UUID problemId,
            @NotNull JudgeLanguage language,
            @NotBlank @Size(max = 100_000) String sourceCode) {}

    public record CaseResult(int ordinalNumber, JudgeStatus status, Long executionTimeMs,
            Long memoryUsedKb, String actualOutput, String stderrOutput) {}

    public record RunResponse(JudgeStatus status, String stdout, String compilerOutput,
            long executionTimeMs, long memoryUsedKb, List<CaseResult> cases) {}

    public record SubmissionResponse(UUID id, UUID problemId, UUID submitterId, UUID testCaseVersionId,
            JudgeLanguage language, JudgeStatus status, Long executionTimeMs, Long memoryUsedKb,
            String compilerOutput, String errorMessage, Instant completedAt, Instant createdAt,
            List<CaseResult> cases) {}

    public record RejudgeResponse(long historyId, UUID submissionId, JudgeStatus previousStatus,
            RejudgeStatus status, Instant requestedAt) {}

    public record RejudgeRequest(@Size(max = 1000) String reason) {}

    public record ProblemRequest(
            @NotNull JudgeContextType contextType,
            @NotNull UUID contextId,
            UUID classroomId,
            @NotBlank @Size(max = 255) String title,
            @NotNull JudgeProblemFormat format,
            @Min(100) @Max(5_000) Integer timeLimitMs,
            @Min(16) @Max(512) Integer memoryLimitMb,
            @NotNull JudgeFeedbackLevel feedbackLevel,
            @NotEmpty Set<JudgeLanguage> enabledLanguages,
            @Size(max = 100_000) String starterCodeJava,
            @Size(max = 100_000) String starterCodePython) {}

    public record ProblemResponse(UUID id, UUID ownerId, JudgeContextType contextType, UUID contextId, UUID classroomId,
            String title, JudgeProblemFormat format, int timeLimitMs, int memoryLimitMb,
            JudgeFeedbackLevel feedbackLevel, Set<JudgeLanguage> enabledLanguages,
            String starterCodeJava, String starterCodePython, boolean active,
            Instant createdAt, Instant updatedAt) {}

    public record ProblemUpdateRequest(
            @Size(min = 1, max = 255) String title,
            JudgeProblemFormat format,
            @Min(100) @Max(5_000) Integer timeLimitMs,
            @Min(16) @Max(512) Integer memoryLimitMb,
            JudgeFeedbackLevel feedbackLevel,
            @Size(min = 1) Set<JudgeLanguage> enabledLanguages,
            @Size(max = 100_000) String starterCodeJava,
            @Size(max = 100_000) String starterCodePython,
            Boolean active) {}

    public record TestCaseInput(
            @NotNull TestCaseVisibility visibility,
            @NotNull @Size(max = 100_000) String input,
            @NotNull @Size(max = 100_000) String expectedOutput,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal weight) {}

    public record PublishTestCaseVersionRequest(
            @NotEmpty @Size(max = 500) List<@Valid TestCaseInput> testCases,
            boolean rejudgeExistingSubmissions) {}

    public record TestCaseVersionResponse(UUID id, UUID problemId, int versionNumber,
            String checksumSha256, boolean active, int testCaseCount, Instant createdAt) {}

    public record SampleTestCaseResponse(int ordinalNumber, String input, String expectedOutput) {}
}
