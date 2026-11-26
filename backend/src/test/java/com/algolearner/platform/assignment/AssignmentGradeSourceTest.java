package com.algolearner.platform.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AssignmentGradeSourceTest {
    @Test
    void highestUsesAttemptMaximumAndLatestKeepsTheFirstGradedAttempt() {
        var latest = new BigDecimal("8");
        var older = new BigDecimal("6");
        assertThat(AssignmentGradeSelection.prefers(ScoringPolicy.HIGHEST, older, new BigDecimal("6"),
                latest, new BigDecimal("10"))).isTrue();
        assertThat(AssignmentGradeSelection.prefers(ScoringPolicy.LATEST, older, new BigDecimal("6"),
                latest, new BigDecimal("10"))).isFalse();
        assertThat(AssignmentGradeSelection.prefers(ScoringPolicy.LATEST, BigDecimal.ZERO, BigDecimal.TEN,
                null, null)).isTrue();
    }
    @Test
    void highestKeepsNewestAtRoundedTieAndComparesLegacyScoreWithoutDenominator() {
        var highest = ScoringPolicy.HIGHEST;
        var latest = BigDecimal.ONE;
        var maximum = BigDecimal.TEN;
        assertThat(AssignmentGradeSelection.prefers(highest, new BigDecimal("1.000000000004"), maximum,
                latest, maximum)).isFalse();
        assertThat(AssignmentGradeSelection.prefers(highest, new BigDecimal("1.000000000006"), maximum,
                latest, maximum)).isTrue();
        assertThat(AssignmentGradeSelection.prefers(highest, new BigDecimal("0.11"), null, latest, maximum)).isTrue();
        assertThat(AssignmentGradeSelection.prefers(highest, latest, maximum, new BigDecimal("0.11"), null)).isFalse();
        assertThat(AssignmentGradeSelection.prefers(ScoringPolicy.LATEST, new BigDecimal("0.11"), null,
                latest, maximum)).isFalse();
    }
}
