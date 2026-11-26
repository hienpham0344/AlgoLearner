package com.algolearner.platform.grading;

import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import com.algolearner.platform.grading.GradeSchemeRevisionEntity.Mode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;

public final class GradebookDtos {
    private GradebookDtos() { }

    public record ItemReference(@NotNull SourceType sourceType, @NotNull UUID sourceId) { }
    public record SchemeItem(@NotNull SourceType sourceType, @NotNull UUID sourceId,
                             BigDecimal weight, Boolean required) {
        public SchemeItem {
            if (required == null) required = true;
        }
        ItemReference reference() { return new ItemReference(sourceType, sourceId); }
    }
    public record SchemePreviewRequest(@NotNull List<@NotNull @Valid ItemReference> items) { }
    public record SchemeApplyRequest(@NotNull Mode mode, @NotNull @PositiveOrZero Long expectedVersion,
                                     @NotNull List<@NotNull @Valid SchemeItem> items, @Size(max = 500) String reason) { }
    public record SchemeView(UUID revisionId, Integer revisionNumber, Mode mode, long version,
                             List<SchemeItem> items, String reason, UUID createdBy, Instant createdAt) { }
    public record GradeTotals(BigDecimal earnedPoints, BigDecimal possiblePoints, BigDecimal percentage) { }
    public record Overall(GradeTotals current, GradeTotals finalGrade) { }
    public record GradeItem(SourceType sourceType, UUID sourceId, String title) { }
    public record StudentIdentity(UUID studentId, String username) { }
    public record TeacherGradeCell(SourceType sourceType, UUID sourceId, BigDecimal earnedPoints, BigDecimal possiblePoints,
                                   GradeSourceResult.Status status, boolean published, boolean rejudgePending,
                                   GradeSourceResult.Provenance provenance, GradeSourceResult.Progress progress) { }
    public record TeacherGradebookRow(StudentIdentity student, List<TeacherGradeCell> grades, Overall overall) { }
    public record ClassGradebookSummary(long students, int items, long gradedCells, long completedCells,
                                        long submissions, long lateSubmissions, BigDecimal averageCurrentPercentage) { }
    public record StudentGrade(SourceType sourceType, UUID sourceId, String title, BigDecimal earnedPoints, BigDecimal possiblePoints) { }
    public enum OverallStatus { UPDATING }
    public record MyGradebookResponse(List<StudentGrade> items,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS) Overall overall,
            OverallStatus overallStatus) { }
    public record VisibilityRequest(@NotNull Boolean showOverall, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record VisibilityView(boolean showOverall, long version) { }
    public record SubmissionAnswer(UUID questionId, JsonNode selectedOptionIds, String essay, String fileUrl, String code) { }
    public record TeacherSubmissionDetail(UUID attemptId, UUID studentId, Instant submittedAt, boolean late,
                                           String status, List<SubmissionAnswer> answers) { }
    public enum PublicationScope { STUDENT, CLASS }
    public record GradePublicationRequest(@NotNull PublicationScope scope, UUID studentId,
                                          @NotNull @PositiveOrZero Long expectedVersion) { }
    public record GradePublicationResponse(UUID publicationId, UUID schemeRevisionId, long version,
                                           int publishedCount, int ineligibleCount, Instant publishedAt) { }
    public record GradebookHistoryEntry(long id, UUID studentId, UUID actorId, UUID schemeRevisionId, String action,
                                        String reason, JsonNode previousValue, JsonNode currentValue, Instant occurredAt) { }
}
