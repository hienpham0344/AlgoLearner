package com.algolearner.platform.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AnnouncementDtos {
    private AnnouncementDtos() { }

    public record CreateRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title cannot exceed 255 characters")
            String title,

            @NotBlank(message = "Content is required")
            String content
    ) { }

    public record UpdateRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title cannot exceed 255 characters")
            String title,

            @NotBlank(message = "Content is required")
            String content
    ) { }

    public record AnnouncementView(
            UUID id,
            UUID classroomId,
            UUID authorId,
            String authorName,
            String title,
            String content,
            boolean isPinned,
            Instant pinnedAt,
            Instant createdAt,
            Instant updatedAt
    ) { }
}
