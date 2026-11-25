package com.algolearner.platform.quiz;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;

public final class QuizPolicy {
    private QuizPolicy() { }
    public static boolean expired(Instant now,Instant cutoff) { return cutoff!=null && !now.isBefore(cutoff); }
    public static Instant cutoff(Instant startedAt,Integer durationSeconds) {
        return durationSeconds==null?null:startedAt.plusSeconds(durationSeconds).truncatedTo(ChronoUnit.MICROS);
    }
    public static void validate(QuizDtos.Configure r) {
        if(r==null || r.title()==null || r.title().isBlank() || r.title().strip().length()>255 || r.maxAttempts()<1
                || r.durationSeconds()!=null && r.durationSeconds()<1
                || r.opensAt()!=null && r.closesAt()!=null && !r.closesAt().isAfter(r.opensAt())
                || r.passingScore()!=null && (!r.graded() || r.passingScore().signum()<0 || r.passingScore().compareTo(java.math.BigDecimal.valueOf(100))>0)
                || r.questions()==null || r.questions().isEmpty()) invalid();
        var ids=new HashSet<java.util.UUID>();
        for(var question:r.questions()) {
            if(question==null || question.id()==null || !ids.add(question.id()) || question.prompt()==null || question.prompt().isBlank()
                    || question.type()==null || question.scoring()==null || question.points()==null || question.points().signum()<=0
                    || question.options()==null || question.options().size()<2) invalid();
            var options=new HashSet<java.util.UUID>(); int correct=0;
            for(var option:question.options()) {
                if(option==null || option.id()==null || !options.add(option.id()) || option.text()==null || option.text().isBlank()) invalid();
                if(option.correct()) correct++;
            }
            if(question.options().stream().anyMatch(o->o.weight()!=null)) {
                if(question.scoring()!=QuizDtos.Scoring.PARTIAL || question.options().stream().anyMatch(o->o.weight()==null
                        || o.weight().signum()<0 || o.weight().scale()>6 || o.weight().compareTo(question.points())>0)
                        || question.options().stream().filter(QuizDtos.Option::correct).map(QuizDtos.Option::weight)
                                .reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).compareTo(question.points())!=0) invalid();
            }
            if(correct==0 || question.type()!=QuizDtos.QuestionType.MULTIPLE_CHOICE && correct!=1
                    || question.type()==QuizDtos.QuestionType.TRUE_FALSE && question.options().size()!=2) invalid();
        }
    }
    private static void invalid(){throw new ApiException(ErrorCode.VALIDATION_FAILED,"Invalid quiz configuration");}
}
