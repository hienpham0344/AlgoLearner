package com.algolearner.platform.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CourseDtos {
    private CourseDtos() { }
    public record Create(@NotBlank @Size(max = 255) String title, String description) { }
    public record UnitRequest(@NotBlank @Size(max = 255) String title, @NotBlank String content) { }
    public record Unit(UUID id, String title, String content, int displayOrder) { }
    public record Summary(UUID id, UUID authorId, String title, String description, String status, Instant createdAt) { }
    public record Detail(Summary course, List<Unit> units) { }
    public record Progress(UUID courseId, UUID accountId, int completedUnits, int totalUnits, double progressPercent) { }
    public record Review(boolean approved, @Size(max = 500) String reason) { }
}
