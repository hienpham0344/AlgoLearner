package com.algolearner.platform.quiz;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;

public final class QuizPolicy {
    private QuizPolicy() { }
    public static boolean expired(Instant now,Instant cutoff) { return cutoff!=null && !now.isBefore(cutoff); }
    public static Instant cutoff(Instant startedAt,Integer durationSeconds,Instant deadline,boolean allowLate) {
        Instant cutoff=durationSeconds==null?null:startedAt.plusSeconds(durationSeconds);
        if (!allowLate && deadline!=null && (cutoff==null || deadline.isBefore(cutoff))) cutoff=deadline;
        return cutoff==null?null:cutoff.truncatedTo(ChronoUnit.MICROS);
    }
    public static void validate(QuizDtos.Configure r) {
        if(r==null || r.title()==null || r.title().isBlank() || r.title().strip().length()>255 || r.maxAttempts()<1
                || r.durationSeconds()!=null && r.durationSeconds()<1
                || r.opensAt()!=null && r.closesAt()!=null && !r.closesAt().isAfter(r.opensAt())
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
            if(correct==0 || question.type()!=QuizDtos.QuestionType.MULTIPLE_CHOICE && correct!=1
                    || question.type()==QuizDtos.QuestionType.TRUE_FALSE && question.options().size()!=2) invalid();
        }
    }
    private static void invalid(){throw new ApiException(ErrorCode.VALIDATION_FAILED,"Invalid quiz configuration");}
}
