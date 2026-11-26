package com.algolearner.platform.grading;

import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import java.math.BigDecimal;
import java.util.UUID;

/** Internal result for a single item and student. Student results contain published scores only. */
public record GradeSourceResult(
        SourceType sourceType,
        UUID sourceId,
        UUID studentId,
        BigDecimal earnedPoints,
        BigDecimal possiblePoints,
        Status status,
        boolean published,
        Provenance provenance,
        boolean rejudgePending,
        Progress progress) {
    public enum Audience { TEACHER, STUDENT }
    public enum Status { GRADED, UNGRADED, PENDING, NOT_SUBMITTED }
    public enum SnapshotKind { ASSIGNMENT_ATTEMPT, QUIZ_ATTEMPT, LEGACY_MISSING }

    public record Provenance(UUID attemptId, UUID evaluationId, Long quizOverrideId,
                             UUID assignmentRevisionId, SnapshotKind snapshotKind) { }

    public record Progress(boolean completed, int submissionCount, int lateSubmissionCount) { }
}
