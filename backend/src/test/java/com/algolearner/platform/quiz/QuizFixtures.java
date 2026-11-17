package com.algolearner.platform.quiz;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static com.algolearner.platform.quiz.QuizDtos.*;

final class QuizFixtures {
    static final Instant NOW=Instant.parse("2026-11-18T00:00:00Z");
    static final UUID QUESTION=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID CORRECT=UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID WRONG=UUID.fromString("00000000-0000-0000-0000-000000000003");
    static Config config(Timeout timeout, Publication publication) {
        return new Config(Kind.GRADED,2,null,null,false,60,timeout,false,false,Scoring.ALL_OR_NOTHING,
                Completion.SUBMIT_IS_ENOUGH,null,publication,true,true,List.of(
                new Question(QUESTION,"2 + 2?",QuestionType.SINGLE_CHOICE,BigDecimal.ONE,"Addition",List.of(
                        new Option(CORRECT,"4",true,null),new Option(WRONG,"5",false,null)))));
    }
    static Config timed(Instant start,Instant deadline,boolean late) {
        var c=config(Timeout.AUTO_SUBMIT,Publication.AUTO);
        return new Config(c.kind(),c.maxAttempts(),start,deadline,late,c.timeLimitSeconds(),c.timeout(),c.shuffleQuestions(),
                c.shuffleAnswers(),c.scoring(),c.completion(),c.passingScore(),c.publication(),c.showCorrectAnswers(),c.showExplanations(),c.questions());
    }
}
