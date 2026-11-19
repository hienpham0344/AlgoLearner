package com.algolearner.platform.assignment;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** Normalize each positive-point question before weighting; round only aggregate output. */
final class AssignmentScoreCalculator {
    private AssignmentScoreCalculator() { }
    record Scores(BigDecimal automatic,BigDecimal manual,BigDecimal total,boolean complete) { }
    static Scores calculate(AssignmentAttemptEntity attempt,List<AssignmentAnswerEntity> answers,List<AssignmentQuestionEntity> legacyQuestions) {
        if(attempt.getRevisionSnapshot()==null) {
            boolean complete=!legacyQuestions.isEmpty() && legacyQuestions.stream().allMatch(q->answers.stream()
                    .anyMatch(a->a.getQuestion().getId().equals(q.getId()) && a.getScore()!=null));
            var auto=sum(answers,false);var manual=sum(answers,true);
            return new Scores(auto,manual,auto.add(manual),complete);
        }
        var questions=AssignmentSnapshotPolicy.decode(attempt.getRevisionSnapshot()).questions();
        BigDecimal maximum=BigDecimal.ZERO,denominator=BigDecimal.ZERO,auto=BigDecimal.ZERO,manual=BigDecimal.ZERO;
        boolean complete=true;
        for(var question:questions) {
            var answer=answers.stream().filter(a->a.getQuestion().getId().equals(question.id())).findFirst().orElse(null);
            var earned=answer==null?null:answer.getScore();
            if((question.type()==QuestionType.ESSAY || question.type()==QuestionType.CODE) && earned==null)complete=false;
            if(question.points()==null || question.points().signum()<=0)continue;
            var weight=question.weight()==null?BigDecimal.ONE:question.weight();
            maximum=maximum.add(question.points());denominator=denominator.add(weight);
            var contribution=(earned==null?BigDecimal.ZERO:earned).divide(question.points(),MathContext.DECIMAL128).multiply(weight);
            if(question.type()==QuestionType.ESSAY)manual=manual.add(contribution);else auto=auto.add(contribution);
        }
        if(denominator.signum()==0)return new Scores(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,complete);
        var factor=maximum.divide(denominator,MathContext.DECIMAL128);
        var rawAuto=auto.multiply(factor);var rawManual=manual.multiply(factor);
        return new Scores(round(rawAuto),round(rawManual),round(rawAuto.add(rawManual)),complete);
    }
    private static BigDecimal sum(List<AssignmentAnswerEntity> answers,boolean manual) {
        return answers.stream().filter(a->(a.getQuestion().getQuestionType()==QuestionType.ESSAY)==manual)
                .map(AssignmentAnswerEntity::getScore).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private static BigDecimal round(BigDecimal value){return value.setScale(2,RoundingMode.HALF_UP);}
}
