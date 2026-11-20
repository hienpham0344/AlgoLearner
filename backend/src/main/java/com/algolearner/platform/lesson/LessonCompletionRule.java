package com.algolearner.platform.lesson;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record LessonCompletionRule(@NotNull Operator operator, @NotEmpty List<@Valid Condition> conditions) {
    public enum Operator { ALL, ANY }
    public enum Requirement { SUBMITTED, SCORE_AT_LEAST }
    public record Condition(@NotNull LinkedItemType itemType, @NotNull UUID itemId, @NotNull Requirement requirement,
                            @DecimalMin("0") @DecimalMax("100") BigDecimal scorePercent) { }
}
