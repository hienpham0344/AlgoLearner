package com.algolearner.platform.practice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Request/response contracts for SRS 4.8. Timestamps serialize as ISO-8601 UTC. Practice is ungraded and has no
 * deadline, so no response carries a score or deadline; requests that try to set one are rejected.
 */
public final class PracticeDtos {
    private PracticeDtos() { }

    public enum CompletionStatus { COMPLETED, NOT_COMPLETED }

    // ---- requests ----
    @Schema(description = "Create a classroom practice. deadline and maxScore exist only so they can be rejected (400).")
    public record CreatePractice(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 10000) String description,
            PracticeProblemType problemType,
            UUID lessonId,
            @NotEmpty Set<PracticeLanguage> allowedLanguages,
            Map<PracticeLanguage, @Size(max = 20000) String> starterCode,
            @Min(100) @Max(10000) Integer timeLimitMs,
            @Min(16) @Max(256) Integer memoryLimitMb,
            @Schema(description = "DRAFT (default) or PUBLISHED") PracticeStatus status,
            @Valid @Size(max = 100) List<TestcaseInput> testcases,
            Instant deadline,
            Integer maxScore) { }

    @Schema(description = "Partial update; omitted fields keep their value")
    public record UpdatePractice(
            @Size(min = 1, max = 255) String title,
            @Size(max = 10000) String description,
            PracticeProblemType problemType,
            UUID lessonId,
            Set<PracticeLanguage> allowedLanguages,
            Map<PracticeLanguage, @Size(max = 20000) String> starterCode,
            @Min(100) @Max(10000) Integer timeLimitMs,
            @Min(16) @Max(256) Integer memoryLimitMb,
            Instant deadline,
            Integer maxScore) { }

    public record ChangeStatus(@NotNull PracticeStatus status) { }

    public record TestcaseInput(
            @NotNull @Size(max = 100000) String input,
            @NotNull @Size(max = 100000) String expectedOutput,
            boolean sample,
            @Min(0) Integer orderIndex) { }

    public record CodeRequest(
            @NotNull PracticeLanguage language,
            @NotBlank @Size(max = 100000) String sourceCode) { }

    @Schema(description = "RUN request. Without customInput the sample testcases run; with it only that input runs (max 64 KB).")
    public record RunInput(
            @NotNull PracticeLanguage language,
            @NotBlank @Size(max = 100000) String sourceCode,
            String customInput) { }

    // ---- responses ----
    public record PracticeSummary(UUID id, UUID classroomId, UUID lessonId, String title, PracticeProblemType problemType,
                                  PracticeStatus status, boolean available, Set<PracticeLanguage> allowedLanguages,
                                  int timeLimitMs, int memoryLimitMb, CompletionStatus completionStatus,
                                  Instant createdAt, Instant updatedAt) { }

    /** Visible to students: carries sample testcases only. */
    public record SampleTestcase(UUID id, String input, String expectedOutput, int orderIndex) { }

    public record PracticeDetail(UUID id, UUID classroomId, UUID lessonId, String title, String description,
                                 PracticeProblemType problemType, Map<PracticeLanguage, String> starterCode,
                                 Set<PracticeLanguage> allowedLanguages, int timeLimitMs, int memoryLimitMb,
                                 PracticeStatus status, boolean available, int testcaseVersion,
                                 CompletionStatus completionStatus, List<SampleTestcase> sampleTestcases,
                                 Instant createdAt, Instant updatedAt) { }

    /** Teacher-only view including hidden testcases. */
    public record TeacherTestcase(UUID id, int version, String input, String expectedOutput, boolean sample, int orderIndex) { }

    public record RunCaseResult(int index, String input, String expectedOutput, PracticeVerdict verdict,
                                String stdout, String stderr, Integer runtimeMs) { }

    public record RunResponse(PracticeVerdict verdict, List<RunCaseResult> cases) { }

    public record SubmissionView(UUID id, UUID practiceId, UUID studentId, String studentUsername,
                                 PracticeLanguage language, String code, int testcaseVersion, PracticeVerdict verdict,
                                 Integer runtimeMs, Integer memoryKb, RejudgeStatus rejudgeStatus, Instant submittedAt) { }

    public record ProgressView(UUID studentId, String username, CompletionStatus status, Instant completedAt) { }
}
