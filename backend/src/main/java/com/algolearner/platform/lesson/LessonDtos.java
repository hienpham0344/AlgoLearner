package com.algolearner.platform.lesson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LessonDtos {
    private LessonDtos() { }

    public record CompletionRuleView(long version, LessonCompletionRule rule) { }

    public record StudentProgressView(UUID studentId, String username, boolean completed, Instant completedAt) { }

    public record CreateLessonRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title cannot exceed 255 characters")
            String title,
            String content,
            String codeExample,
            String externalLinks,
            CompletionMode completionMode
    ) { }

    public record UpdateLessonRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title cannot exceed 255 characters")
            String title,
            String content,
            String codeExample,
            String externalLinks,
            CompletionMode completionMode
    ) { }

    public record LinkItemRequest(
            @NotNull(message = "Item type is required")
            LinkedItemType itemType,
            @NotNull(message = "Item ID is required")
            UUID itemId
    ) { }

    public record AddMaterialRequest(UUID fileId, String fileName, String fileUrl, MaterialFileType fileType, long fileSize) {
        public AddMaterialRequest(String fileName, String fileUrl, MaterialFileType fileType, long fileSize) {
            this(null, fileName, fileUrl, fileType, fileSize);
        }
    }

    public record MaterialView(
            UUID id,
            String fileName,
            String fileUrl,
            MaterialFileType fileType,
            long fileSize,
            Instant createdAt
    ) { }

    public record CapabilitiesView(
            boolean uploadsAvailable,
            boolean activityLinkingAvailable,
            long maxFileSizeBytes,
            List<String> allowedExtensions
    ) { }

    public record ActivityOptionView(
            UUID id,
            LinkedItemType type,
            String title,
            UUID linkedLessonId,
            boolean available
    ) { }

    public record LinkedItemView(
            UUID id,
            LinkedItemType itemType,
            UUID itemId,
            int displayOrder,
            String title,
            Boolean available,
            Boolean completed
    ) {
        public LinkedItemView(UUID id, LinkedItemType itemType, UUID itemId, int displayOrder) {
            this(id, itemType, itemId, displayOrder, null, true, false);
        }

        public LinkedItemView(UUID id, LinkedItemType itemType, UUID itemId, int displayOrder, String title, Boolean available) {
            this(id, itemType, itemId, displayOrder, title, available, false);
        }
    }

    public record LessonSummaryView(
            UUID id,
            UUID classroomId,
            String title,
            LessonStatus status,
            CompletionMode completionMode,
            int displayOrder,
            Boolean isCompleted,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            String content,
            String codeExample,
            int materialCount
    ) {
        public LessonSummaryView(
                UUID id,
                UUID classroomId,
                String title,
                LessonStatus status,
                CompletionMode completionMode,
                int displayOrder,
                Boolean isCompleted,
                Instant completedAt,
                Instant createdAt,
                Instant updatedAt
        ) {
            this(id, classroomId, title, status, completionMode, displayOrder, isCompleted, completedAt, createdAt, updatedAt, null, null, 0);
        }
    }

    public record LessonDetailView(
            UUID id,
            UUID classroomId,
            String title,
            String content,
            String codeExample,
            String externalLinks,
            LessonStatus status,
            CompletionMode completionMode,
            int displayOrder,
            List<MaterialView> materials,
            List<LinkedItemView> linkedItems,
            Boolean isCompleted,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            LessonStatus previousStatus,
            Boolean canDelete,
            List<UUID> requiredItemIds
    ) {
        public LessonDetailView(
                UUID id,
                UUID classroomId,
                String title,
                String content,
                String codeExample,
                String externalLinks,
                LessonStatus status,
                CompletionMode completionMode,
                int displayOrder,
                List<MaterialView> materials,
                List<LinkedItemView> linkedItems,
                Boolean isCompleted,
                Instant completedAt,
                Instant createdAt,
                Instant updatedAt
        ) {
            this(id, classroomId, title, content, codeExample, externalLinks, status, completionMode, displayOrder,
                    materials, linkedItems, isCompleted, completedAt, createdAt, updatedAt, null, true, List.of());
        }
    }
}
