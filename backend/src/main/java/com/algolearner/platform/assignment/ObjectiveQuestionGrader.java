package com.algolearner.platform.assignment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ObjectiveQuestionGrader {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private ObjectiveQuestionGrader() { }

    static BigDecimal grade(QuestionType type, GradingPolicy policy, BigDecimal points,
            List<AssignmentQuestionOptionEntity> options, String selectedOptionIds) {
        if (points == null || points.signum() <= 0 || options.isEmpty()) return BigDecimal.ZERO;
        if (type != QuestionType.SINGLE_CHOICE && type != QuestionType.TRUE_FALSE
                && type != QuestionType.MULTIPLE_CHOICE) return BigDecimal.ZERO;

        var selected = parseSelection(selectedOptionIds);
        if (selected.isEmpty()) return BigDecimal.ZERO;

        var available = new HashSet<UUID>();
        var correct = new HashSet<UUID>();
        for (var option : options) {
            available.add(option.getId());
            if (option.isCorrect()) correct.add(option.getId());
        }
        if (correct.isEmpty() || !available.containsAll(selected)) return BigDecimal.ZERO;

        if (type == QuestionType.SINGLE_CHOICE || type == QuestionType.TRUE_FALSE) {
            return selected.size() == 1 && correct.size() == 1 && selected.equals(correct)
                    ? points : BigDecimal.ZERO;
        }

        if (policy != GradingPolicy.PARTIAL_CREDIT) {
            return selected.equals(correct) ? points : BigDecimal.ZERO;
        }

        long correctSelections = selected.stream().filter(correct::contains).count();
        return points.multiply(BigDecimal.valueOf(correctSelections))
                .divide(BigDecimal.valueOf(correct.size()), 2, RoundingMode.HALF_UP)
                .min(points);
    }

    private static Set<UUID> parseSelection(String value) {
        if (value == null || value.isBlank()) return Set.of();
        try {
            var array = JSON.readTree(value);
            if (array == null || !array.isArray()) return Set.of();
            var selected = new HashSet<UUID>();
            for (var item : array) {
                if (!item.isTextual()) return Set.of();
                var id = UUID.fromString(item.textValue());
                if (!id.toString().equalsIgnoreCase(item.textValue()) || !selected.add(id)) return Set.of();
            }
            return selected;
        } catch (JsonProcessingException | IllegalArgumentException error) {
            return Set.of();
        }
    }
}
