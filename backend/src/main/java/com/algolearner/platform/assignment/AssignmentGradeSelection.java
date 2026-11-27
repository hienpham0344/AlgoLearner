package com.algolearner.platform.assignment;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class AssignmentGradeSelection {
    private AssignmentGradeSelection() { }

    /** Callers visit graded attempts in descending attempt-number order. */
    static boolean prefers(ScoringPolicy policy, BigDecimal score, BigDecimal maximum,
                           BigDecimal selectedScore, BigDecimal selectedMaximum) {
        return selectedScore == null || policy == ScoringPolicy.HIGHEST
                && normalized(score, maximum).compareTo(normalized(selectedScore, selectedMaximum)) > 0;
    }

    private static BigDecimal normalized(BigDecimal score, BigDecimal maximum) {
        return maximum != null && maximum.signum() > 0 ? score.divide(maximum, 12, RoundingMode.HALF_UP) : score;
    }
}
