package com.algolearner.platform.course;

import com.algolearner.platform.course.CourseDtos.CompletionRule;
import com.algolearner.platform.course.CourseDtos.OptionContent;
import com.algolearner.platform.course.CourseDtos.QuestionContent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** FR-PCRS-010 auto-grade: each question is all-or-nothing; score is a 0..100 percentage. */
public final class PublicQuizGrader {
    private PublicQuizGrader() { }

    public record Graded(BigDecimal score, boolean passed) { }

    public static Graded grade(List<QuestionContent> questions, Map<UUID, Set<UUID>> answers,
                               CompletionRule rule, BigDecimal passingScore) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal earned = BigDecimal.ZERO;
        for (QuestionContent question : questions) {
            total = total.add(question.points());
            if (correct(question, answers.getOrDefault(question.id(), Set.of()))) earned = earned.add(question.points());
        }
        BigDecimal score = total.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : earned.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
        boolean passed = rule == CompletionRule.SUBMIT || (passingScore != null && score.compareTo(passingScore) >= 0);
        return new Graded(score, passed);
    }

    public static boolean correct(QuestionContent question, Set<UUID> selected) {
        Set<UUID> expected = question.options().stream().filter(OptionContent::correct).map(OptionContent::id)
                .collect(Collectors.toSet());
        return expected.equals(selected);
    }
}
