package com.algolearner.platform.quiz;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.algolearner.platform.quiz.QuizDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class QuizGradingTest {
    private static final UUID Q=UUID.randomUUID(), A=UUID.randomUUID(), B=UUID.randomUUID(), C=UUID.randomUUID();
    private static Option option(UUID id, boolean correct, String weight) { return new Option(id,id.toString(),correct,new BigDecimal(weight)); }
    private static Question question() { return new Question(Q,"Select",QuestionType.MULTIPLE_CHOICE,new BigDecimal("4"),"Explanation",
            List.of(option(A,true,"3"),option(B,true,"1"),option(C,false,"2"))); }
    static Config config(Scoring scoring, List<Question> questions) { return new Config(Kind.GRADED,2,null,null,false,60,Timeout.AUTO_SUBMIT,
            true,true,scoring,Completion.PASSING_SCORE,new BigDecimal("50"),Publication.MANUAL,false,false,questions); }

    @Test void exactSetRequiredForAllOrNothing() {
        var c=config(Scoring.ALL_OR_NOTHING,List.of(question()));
        assertEquals(new BigDecimal("100.00"),QuizGrading.score(c,Map.of(Q,List.of(A,B))));
        assertEquals(new BigDecimal("0.00"),QuizGrading.score(c,Map.of(Q,List.of(A))));
        assertEquals(new BigDecimal("0.00"),QuizGrading.score(c,Map.of(Q,List.of(A,B,C))));
    }
    @Test void partialCreditUsesTeacherWeightsAndPenalties() {
        var c=config(Scoring.PARTIAL_CREDIT,List.of(question()));
        assertEquals(new BigDecimal("75.00"),QuizGrading.score(c,Map.of(Q,List.of(A))));
        assertEquals(new BigDecimal("25.00"),QuizGrading.score(c,Map.of(Q,List.of(A,C))));
        assertEquals(new BigDecimal("0.00"),QuizGrading.score(c,Map.of(Q,List.of(B,C))));
        assertEquals(new BigDecimal("0.00"),QuizGrading.score(c,Map.of()));
    }
    @Test void scoreIsWeightedByQuestionPointsAndRoundedOnce() {
        var second=new Question(UUID.randomUUID(),"Second",QuestionType.SINGLE_CHOICE,new BigDecimal("2"),null,
                List.of(option(UUID.randomUUID(),true,"2"),option(UUID.randomUUID(),false,"1")));
        assertEquals(new BigDecimal("66.67"),QuizGrading.score(config(Scoring.ALL_OR_NOTHING,List.of(question(),second)),Map.of(Q,List.of(A,B))));
    }
    @Test void rejectsUnknownQuestionOptionDuplicateSelectionsAndMultipleSingleChoiceAnswers() {
        var c=config(Scoring.ALL_OR_NOTHING,List.of(question()));
        assertThrows(IllegalArgumentException.class,()->QuizGrading.score(c,Map.of(UUID.randomUUID(),List.of(A))));
        assertThrows(IllegalArgumentException.class,()->QuizGrading.score(c,Map.of(Q,List.of(UUID.randomUUID()))));
        assertThrows(IllegalArgumentException.class,()->QuizGrading.score(c,Map.of(Q,List.of(A,A))));
        var single=new Question(Q,"Single",QuestionType.SINGLE_CHOICE,BigDecimal.ONE,null,List.of(option(A,true,"1"),option(C,false,"1")));
        assertThrows(IllegalArgumentException.class,()->QuizGrading.score(config(Scoring.ALL_OR_NOTHING,List.of(single)),Map.of(Q,List.of(A,C))));
    }
    @Test void partialWeightsMustSumToQuestionPoints() {
        var invalid=new Question(Q,"Invalid",QuestionType.MULTIPLE_CHOICE,BigDecimal.ONE,null,question().options());
        assertThrows(IllegalArgumentException.class,()->QuizGrading.validate(config(Scoring.PARTIAL_CREDIT,List.of(invalid))));
    }
    @Test void rejectsDuplicateIdsInvalidCardinalityAndEmptyQuiz() {
        assertThrows(IllegalArgumentException.class,()->QuizGrading.validate(config(Scoring.ALL_OR_NOTHING,List.of(question(),question()))));
        assertThrows(IllegalArgumentException.class,()->QuizGrading.validate(config(Scoring.ALL_OR_NOTHING,List.of())));
        var invalid=new Question(Q,"True/false",QuestionType.TRUE_FALSE,BigDecimal.ONE,null,question().options());
        assertThrows(IllegalArgumentException.class,()->QuizGrading.validate(config(Scoring.ALL_OR_NOTHING,List.of(invalid))));
        var noCorrect=new Question(Q,"Invalid",QuestionType.SINGLE_CHOICE,BigDecimal.ONE,null,List.of(option(A,false,"1"),option(C,false,"1")));
        assertThrows(IllegalArgumentException.class,()->QuizGrading.validate(config(Scoring.ALL_OR_NOTHING,List.of(noCorrect))));
    }
    @Test void rejectsNullFieldsAndInvalidWindow() {
        assertThrows(IllegalArgumentException.class,()->QuizGrading.validate(null));
        var c=config(Scoring.ALL_OR_NOTHING,List.of(question()));
        var invalid=new Config(c.kind(),0,Instant.parse("2026-11-18T00:00:00Z"),Instant.parse("2026-11-17T00:00:00Z"),false,0,c.timeout(),false,false,c.scoring(),c.completion(),null,c.publication(),false,false,c.questions());
        assertThrows(IllegalArgumentException.class,()->QuizGrading.validate(invalid));
    }
    @Test void shufflePreservesConfigurationIdentityAndSource() {
        var c=config(Scoring.PARTIAL_CREDIT,List.of(question()));
        var shuffled=QuizGrading.shuffled(c);
        assertEquals(c.questions().getFirst().id(),shuffled.questions().getFirst().id());
        assertEquals(new HashSet<>(question().options()),new HashSet<>(shuffled.questions().getFirst().options()));
        assertEquals(c.passingScore(),shuffled.passingScore());
        assertEquals(List.of(A,B,C),c.questions().getFirst().options().stream().map(Option::id).toList());
        assertEquals(QuizGrading.score(c,Map.of(Q,List.of(A,C))),QuizGrading.score(shuffled,Map.of(Q,List.of(A,C))));
    }
    @Test void redactsCorrectnessAndExplanationsIndependently() {
        var c=config(Scoring.PARTIAL_CREDIT,List.of(question()));
        var hidden=QuizGrading.questionViews(c,false,false).getFirst();
        assertNull(hidden.explanation()); assertTrue(hidden.options().stream().allMatch(o->o.correct()==null));
        assertEquals("Explanation",QuizGrading.questionViews(c,false,true).getFirst().explanation());
        assertTrue(QuizGrading.questionViews(c,true,false).getFirst().options().getFirst().correct());
    }
    @Test void trueFalseGradesAndDisabledShufflePreservesOrder() {
        var question=new Question(Q,"True or false",QuestionType.TRUE_FALSE,BigDecimal.ONE,null,
                List.of(option(A,true,"1"),option(C,false,"1")));
        var c=new Config(Kind.UNGRADED,1,null,null,false,null,Timeout.LOCK_WITHOUT_SUBMIT,
                false,false,Scoring.ALL_OR_NOTHING,Completion.SUBMIT_IS_ENOUGH,null,Publication.AUTO,false,false,List.of(question));
        assertEquals(c,QuizGrading.shuffled(c));
        assertEquals(new BigDecimal("100.00"),QuizGrading.score(c,Map.of(Q,List.of(A))));
        assertEquals(new BigDecimal("0.00"),QuizGrading.score(c,Map.of(Q,List.of(C))));
    }
    @Test void rejectsNegativeWeightsAndGlobalIdentifierCollisions() {
        var negative=new Question(Q,"Negative",QuestionType.SINGLE_CHOICE,BigDecimal.ONE,null,
                List.of(option(A,true,"1"),option(C,false,"-1")));
        assertThrows(IllegalArgumentException.class,()->QuizGrading.validate(config(Scoring.PARTIAL_CREDIT,List.of(negative))));
        var collision=new Question(Q,"Collision",QuestionType.SINGLE_CHOICE,BigDecimal.ONE,null,
                List.of(option(Q,true,"1"),option(C,false,"1")));
        assertThrows(IllegalArgumentException.class,()->QuizGrading.validate(config(Scoring.ALL_OR_NOTHING,List.of(collision))));
    }
    @Test void rejectsWeightsAboveQuestionPointsHugeExponentsAndExcessFractionalPrecision() {
        for (String weight:List.of("1.000001","1E+100000000","0.0000001")) {
            var invalid=new Question(Q,"Invalid weight",QuestionType.SINGLE_CHOICE,BigDecimal.ONE,null,
                    List.of(option(A,true,"1"),option(C,false,weight)));
            assertThrows(IllegalArgumentException.class,()->QuizGrading.validate(config(Scoring.PARTIAL_CREDIT,List.of(invalid))),weight);
        }
    }
    @Test void permitsSixFractionalDigitsAndIntegralExponentWeights() {
        var valid=new Question(Q,"Valid weight",QuestionType.SINGLE_CHOICE,BigDecimal.ONE,null,
                List.of(option(A,true,"1E+0"),option(C,false,"0.000001")));
        assertDoesNotThrow(()->QuizGrading.validate(config(Scoring.PARTIAL_CREDIT,List.of(valid))));
    }
}
