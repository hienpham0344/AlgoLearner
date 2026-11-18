package com.algolearner.platform.lesson;

import java.math.BigDecimal;
import java.util.UUID;

/** sourceVersion is stable on retries and changes whenever the official aggregate or published evaluation changes. */
public record LessonProgressSource(boolean submitted, boolean publishedFinalEvaluation, BigDecimal scorePercent,
                                   UUID evaluationId, String sourceVersion) {
    public LessonProgressSource(boolean submitted, boolean publishedFinalEvaluation, BigDecimal scorePercent) {
        this(submitted, publishedFinalEvaluation, scorePercent, null, null);
    }
}
