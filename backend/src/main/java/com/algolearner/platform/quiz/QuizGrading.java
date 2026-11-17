package com.algolearner.platform.quiz;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;
import static com.algolearner.platform.quiz.QuizDtos.*;

/** Pure operations over the immutable definition saved in each attempt snapshot. */
public final class QuizGrading {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Random RANDOM = new SecureRandom();
    private QuizGrading() { }

    public static void validate(Config config) {
        require(config != null, "Quiz configuration is required");
        require(config.kind()!=null && config.timeout()!=null && config.scoring()!=null
                && config.completion()!=null && config.publication()!=null, "Quiz modes are required");
        require(config.maxAttempts()>=1 && config.maxAttempts()<=10000,"Maximum attempts must be between 1 and 10000");
        require(config.timeLimitSeconds()==null || config.timeLimitSeconds()>=1 && config.timeLimitSeconds()<=604800,
                "Time limit must be between 1 and 604800 seconds");
        require(config.startTime()==null || config.deadline()==null || config.deadline().isAfter(config.startTime()),
                "Deadline must follow start time");
        require(config.passingScore()==null || inRange(config.passingScore(),BigDecimal.ZERO,HUNDRED),"Passing score must be between 0 and 100");
        require(config.completion()!=Completion.PASSING_SCORE || config.passingScore()!=null,"Passing score is required");
        require(config.questions()!=null && !config.questions().isEmpty() && config.questions().size()<=200,
                "Quiz must contain between 1 and 200 questions");
        Set<UUID> identifiers=new HashSet<>();
        for (Question question:config.questions()) {
            require(question!=null && question.id()!=null && identifiers.add(question.id()),"Question identifiers must be unique");
            require(text(question.text(),20000) && question.type()!=null,"Question text and type are required");
            require(question.points()!=null && inRange(question.points(),new BigDecimal("0.01"),new BigDecimal("1000")),
                    "Question points must be between 0.01 and 1000");
            require(question.explanation()==null || question.explanation().length()<=20000,"Explanation is too long");
            require(question.options()!=null && question.options().size()>=2 && question.options().size()<=20,
                    "Question must contain between 2 and 20 options");
            int correct=0;
            BigDecimal correctWeight=BigDecimal.ZERO;
            for (Option option:question.options()) {
                require(option!=null && option.id()!=null && identifiers.add(option.id()),"Option identifiers must be globally unique");
                require(text(option.text(),4000) && option.correct()!=null,"Option text and correctness are required");
                require(option.weight()==null || option.weight().signum()>=0
                        && option.weight().compareTo(question.points())<=0 && option.weight().scale()<=6,
                        "Option weight must be between zero and question points with at most six fractional digits");
                if (config.scoring()==Scoring.PARTIAL_CREDIT) require(option.weight()!=null,"Partial credit requires every option weight");
                if (Boolean.TRUE.equals(option.correct())) {
                    correct++;
                    if (option.weight()!=null) correctWeight=correctWeight.add(option.weight());
                }
            }
            require(correct>0 && correct<question.options().size(),"Question requires correct and incorrect options");
            require(question.type()==QuestionType.MULTIPLE_CHOICE || correct==1,"Single choice and True/False require exactly one correct option");
            require(question.type()!=QuestionType.TRUE_FALSE || question.options().size()==2,"True/False requires exactly two options");
            if (config.scoring()==Scoring.PARTIAL_CREDIT) require(correctWeight.compareTo(question.points())==0,
                    "Correct option weights must sum to question points");
        }
    }

    /** Percentage score, rounded once after summing weighted question points. */
    public static BigDecimal score(Config config, Map<UUID,List<UUID>> answers) {
        validate(config);
        require(answers!=null,"Answers are required");
        var questions=new HashMap<UUID,Question>();
        for (Question question:config.questions()) questions.put(question.id(),question);
        for (var entry:answers.entrySet()) {
            Question question=questions.get(entry.getKey());
            require(question!=null,"Answer references an unknown question");
            validateAnswer(question,entry.getValue());
        }
        BigDecimal earned=BigDecimal.ZERO, possible=BigDecimal.ZERO;
        for (Question question:config.questions()) {
            possible=possible.add(question.points());
            Set<UUID> selected=new HashSet<>(answers.getOrDefault(question.id(),List.of()));
            if (config.scoring()==Scoring.ALL_OR_NOTHING) {
                Set<UUID> expected=new HashSet<>();
                for (Option option:question.options()) if (option.correct()) expected.add(option.id());
                if (selected.equals(expected)) earned=earned.add(question.points());
            } else {
                BigDecimal questionScore=BigDecimal.ZERO;
                for (Option option:question.options()) if (selected.contains(option.id()))
                    questionScore=questionScore.add(option.correct()?option.weight():option.weight().negate());
                earned=earned.add(questionScore.max(BigDecimal.ZERO).min(question.points()));
            }
        }
        return earned.multiply(HUNDRED).divide(possible,2,RoundingMode.HALF_UP);
    }

    public static void validateAnswer(Question question,List<UUID> selected) {
        require(selected!=null,"Selected options are required");
        require(selected.size()==new HashSet<>(selected).size(),"Duplicate option selection");
        require(question.type()==QuestionType.MULTIPLE_CHOICE || selected.size()<=1,"Question allows only one selection");
        Set<UUID> allowed=new HashSet<>();
        for (Option option:question.options()) allowed.add(option.id());
        require(selected.stream().allMatch(id->id!=null && allowed.contains(id)),"Selected option does not belong to this question");
    }

    public static Config shuffled(Config config) {
        validate(config);
        var questions=new ArrayList<Question>();
        for (Question question:config.questions()) {
            var options=new ArrayList<>(question.options());
            if (config.shuffleAnswers()) Collections.shuffle(options,RANDOM);
            questions.add(new Question(question.id(),question.text(),question.type(),question.points(),question.explanation(),List.copyOf(options)));
        }
        if (config.shuffleQuestions()) Collections.shuffle(questions,RANDOM);
        return new Config(config.kind(),config.maxAttempts(),config.startTime(),config.deadline(),config.allowLateAttempts(),
                config.timeLimitSeconds(),config.timeout(),config.shuffleQuestions(),config.shuffleAnswers(),config.scoring(),
                config.completion(),config.passingScore(),config.publication(),config.showCorrectAnswers(),config.showExplanations(),List.copyOf(questions));
    }

    public static List<QuestionView> questionViews(Config config,boolean correct,boolean explanations) {
        return config.questions().stream().map(question->new QuestionView(question.id(),question.text(),question.type(),question.points(),
                explanations?question.explanation():null,question.options().stream().map(option->
                    new OptionView(option.id(),option.text(),correct?option.correct():null)).toList())).toList();
    }

    private static boolean text(String value,int max) { return value!=null && !value.isBlank() && value.length()<=max; }
    private static boolean inRange(BigDecimal value,BigDecimal min,BigDecimal max) { return value.compareTo(min)>=0 && value.compareTo(max)<=0; }
    private static void require(boolean condition,String message) { if (!condition) throw new IllegalArgumentException(message); }
}
