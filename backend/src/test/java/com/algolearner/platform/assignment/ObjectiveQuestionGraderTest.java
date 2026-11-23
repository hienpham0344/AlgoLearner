package com.algolearner.platform.assignment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectiveQuestionGraderTest {
    private static final UUID CORRECT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_CORRECT = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID THIRD_CORRECT = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID WRONG = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FOREIGN = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void singleChoiceAndTrueFalseRequireExactlyOneCorrectOption() {
        var options = List.of(option(CORRECT, true), option(WRONG, false));
        for (var type : List.of(QuestionType.SINGLE_CHOICE, QuestionType.TRUE_FALSE)) {
            assertThat(grade(type, GradingPolicy.ALL_OR_NOTHING, "10", options, selected(CORRECT)))
                    .isEqualByComparingTo("10");
            assertThat(grade(type, GradingPolicy.ALL_OR_NOTHING, "10", options, selected(WRONG)))
                    .isZero();
            assertThat(grade(type, GradingPolicy.ALL_OR_NOTHING, "10", options, selected(CORRECT, WRONG)))
                    .isZero();
        }
    }

    @Test
    void invalidOrMissingSelectionNeverReceivesPoints() {
        var options = List.of(option(CORRECT, true), option(WRONG, false));
        for (String selection : Arrays.asList(null, "", " ", "null", "[]", "{}", "[", "[1]",
                "[null]", "[\"not-a-uuid\"]", "\"" + CORRECT + "\"",
                "[\"prefix" + CORRECT + "suffix\"]", selected(CORRECT) + " null")) {
            assertThat(grade(QuestionType.SINGLE_CHOICE, GradingPolicy.ALL_OR_NOTHING, "10", options, selection))
                    .as("selection %s", selection).isZero();
        }
    }

    @Test
    void duplicateOrForeignOptionsNeverReceivePoints() {
        var options = List.of(option(CORRECT, true), option(WRONG, false));
        for (var type : List.of(QuestionType.SINGLE_CHOICE, QuestionType.TRUE_FALSE, QuestionType.MULTIPLE_CHOICE)) {
            for (var policy : GradingPolicy.values()) {
                assertThat(grade(type, policy, "10", options, selected(CORRECT, CORRECT))).isZero();
                assertThat(grade(type, policy, "10", options, selected(CORRECT, FOREIGN))).isZero();
            }
        }
    }

    @Test
    void allOrNothingRequiresTheExactCorrectSet() {
        var options = List.of(option(CORRECT, true), option(SECOND_CORRECT, true), option(WRONG, false));
        assertThat(grade(QuestionType.MULTIPLE_CHOICE, GradingPolicy.ALL_OR_NOTHING, "10", options,
                selected(SECOND_CORRECT, CORRECT))).isEqualByComparingTo("10");
        assertThat(grade(QuestionType.MULTIPLE_CHOICE, GradingPolicy.ALL_OR_NOTHING, "10", options,
                selected(CORRECT))).isZero();
        assertThat(grade(QuestionType.MULTIPLE_CHOICE, GradingPolicy.ALL_OR_NOTHING, "10", options,
                selected(CORRECT, SECOND_CORRECT, WRONG))).isZero();
    }

    @Test
    void partialCreditKeepsPointsForCorrectSelectionsAlongsideWrongSelections() {
        var options = List.of(option(CORRECT, true), option(SECOND_CORRECT, true), option(WRONG, false));
        assertThat(grade(QuestionType.MULTIPLE_CHOICE, GradingPolicy.PARTIAL_CREDIT, "10", options,
                selected(CORRECT, WRONG))).isEqualByComparingTo("5");
        assertThat(grade(QuestionType.MULTIPLE_CHOICE, GradingPolicy.PARTIAL_CREDIT, "10", options,
                selected(CORRECT, SECOND_CORRECT, WRONG))).isEqualByComparingTo("10");
        assertThat(grade(QuestionType.MULTIPLE_CHOICE, GradingPolicy.PARTIAL_CREDIT, "10", options,
                selected(WRONG))).isZero();
    }

    @Test
    void partialCreditRoundsOnlyAfterMultiplyingTheCorrectRatio() {
        var options = List.of(option(CORRECT, true), option(SECOND_CORRECT, true), option(THIRD_CORRECT, true));
        assertThat(grade(QuestionType.MULTIPLE_CHOICE, GradingPolicy.PARTIAL_CREDIT, "10", options,
                selected(CORRECT, SECOND_CORRECT))).isEqualByComparingTo("6.67");
        assertThat(grade(QuestionType.MULTIPLE_CHOICE, GradingPolicy.PARTIAL_CREDIT, "10", options,
                selected(CORRECT, SECOND_CORRECT, THIRD_CORRECT))).isEqualByComparingTo("10");
        assertThat(grade(QuestionType.MULTIPLE_CHOICE, GradingPolicy.PARTIAL_CREDIT, "0.02", options,
                selected(CORRECT, SECOND_CORRECT, THIRD_CORRECT))).isEqualByComparingTo("0.02");
    }

    @Test
    void questionsWithNoCorrectAnswerNeverReceivePoints() {
        var options = List.of(option(WRONG, false));
        for (var policy : GradingPolicy.values()) {
            assertThat(grade(QuestionType.MULTIPLE_CHOICE, policy, "10", options, selected(WRONG))).isZero();
            assertThat(grade(QuestionType.MULTIPLE_CHOICE, policy, "10", List.of(), "[]")).isZero();
        }
    }

    @Test
    void nonObjectiveTypesNeverReceiveAnObjectiveScore() {
        var options = List.of(option(CORRECT, true));
        for (var type : List.of(QuestionType.CODE, QuestionType.ESSAY)) {
            assertThat(grade(type, GradingPolicy.ALL_OR_NOTHING, "10", options, selected(CORRECT))).isZero();
        }
    }

    private static BigDecimal grade(QuestionType type, GradingPolicy policy, String points,
            List<AssignmentQuestionOptionEntity> options, String selection) {
        return ObjectiveQuestionGrader.grade(type, policy, new BigDecimal(points), options, selection);
    }

    private static AssignmentQuestionOptionEntity option(UUID id, boolean correct) {
        return AssignmentQuestionOptionEntity.builder().id(id).isCorrect(correct).build();
    }

    private static String selected(UUID... ids) {
        return Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }
}
