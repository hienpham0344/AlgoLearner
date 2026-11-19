package com.algolearner.platform.judge;

import java.util.UUID;

record SubmissionQueuedEvent(UUID evaluationId) {}
record RejudgeQueuedEvent(long historyId) {}
record SubmissionResultChangedEvent(UUID submissionId, UUID submitterId,
        JudgeTypes.JudgeStatus previousStatus, JudgeTypes.JudgeStatus newStatus) {}
