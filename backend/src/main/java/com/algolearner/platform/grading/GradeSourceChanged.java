package com.algolearner.platform.grading;

import java.util.UUID;

/** Emitted within the Assignment or Quiz transaction that changed official grades. */
public record GradeSourceChanged(UUID classroomId, UUID studentId) { }
