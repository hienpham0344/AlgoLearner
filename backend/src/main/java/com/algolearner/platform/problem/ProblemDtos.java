package com.algolearner.platform.problem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProblemDtos {
    private ProblemDtos() {}

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public enum Order {
        NEWEST,
        DIFFICULTY,
        POPULARITY
    }

    public enum ReviewDecision {
        APPROVE,
        CHANGES_REQUESTED,
        REJECT
    }

    public enum Publication {
        PUBLISHED,
        UNPUBLISHED,
        ARCHIVED
    }

    public enum ReportReason {
        WRONG_TEST_CASE,
        INCORRECT_DESCRIPTION,
        INCORRECT_EDITORIAL,
        INAPPROPRIATE_CONTENT,
        OTHER
    }

    public enum ReportStatus {
        OPEN,
        IN_REVIEW,
        RESOLVED,
        REJECTED
    }

    public record ReportRequest(
            @NotNull ReportReason reason, @NotBlank @Size(max = 5000) String description) {}

    public record Report(
            UUID id,
            UUID problemId,
            UUID reporterId,
            ReportReason reason,
            String description,
            ReportStatus status,
            boolean hasImage,
            Instant createdAt) {}

    public record CatalogItem(
            UUID id,
            String title,
            String status,
            Instant createdAt,
            Difficulty difficulty,
            List<String> tags,
            long solvedCount,
            boolean solved) {}
}
