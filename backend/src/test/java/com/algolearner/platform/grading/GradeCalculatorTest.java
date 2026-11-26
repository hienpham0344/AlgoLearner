package com.algolearner.platform.grading;

import static com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType.ASSIGNMENT;
import static com.algolearner.platform.grading.GradeSchemeRevisionEntity.Mode.RAW;
import static com.algolearner.platform.grading.GradeSchemeRevisionEntity.Mode.WEIGHTED;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GradeCalculatorTest {
    private final GradeCalculator calculator = new GradeCalculator();
    private static final UUID STUDENT = UUID.randomUUID();

    @Test
    void rawCountsZeroAndSkipsMissingOrZeroMaximumWhileFinalRequiresGrading() {
        var first = item("25");
        var second = item("25");
        var third = item("25");
        var fourth = item("25");
        var rows = List.of(score(first, "2", "4"), score(second, "0", "6"), score(third, "0", "0"));
        var result = calculator.calculate(RAW, List.of(first, second, third, fourth), rows);
        assertThat(result.current().percentage()).isEqualByComparingTo("20");
        assertThat(result.current().possiblePoints()).isEqualByComparingTo("10");
        assertThat(result.finalGrade()).isNull();
        assertThat(calculator.calculate(RAW, List.of(first, second, third), rows).finalGrade())
                .isEqualTo(result.current());
        assertThat(calculator.calculate(RAW, List.of(third), rows).current()).isNull();
    }

    @Test
    void weightedRenormalizesCurrentAndExcludesLegacyWithoutMaximum() {
        var first = item("20");
        var second = item("30");
        var third = item("50");
        var rows = List.of(score(first, "5", "10"), score(second, "0", "100"), score(third, "9", null));
        var result = calculator.calculate(WEIGHTED, List.of(first, second, third), rows);
        assertThat(result.current().earnedPoints()).isEqualByComparingTo("10");
        assertThat(result.current().possiblePoints()).isEqualByComparingTo("50");
        assertThat(result.current().percentage()).isEqualByComparingTo("20");
        assertThat(result.finalGrade()).isNull();
    }

    private static GradebookDtos.SchemeItem item(String weight) {
        return new GradebookDtos.SchemeItem(ASSIGNMENT, UUID.randomUUID(), new BigDecimal(weight), true);
    }

    private static GradeSourceResult score(GradebookDtos.SchemeItem item, String earned, String possible) {
        return new GradeSourceResult(ASSIGNMENT, item.sourceId(), STUDENT, new BigDecimal(earned),
                possible == null ? null : new BigDecimal(possible), GradeSourceResult.Status.GRADED, true, null, false, null);
    }
}
