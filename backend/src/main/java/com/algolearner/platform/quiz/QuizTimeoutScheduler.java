package com.algolearner.platform.quiz;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@lombok.extern.slf4j.Slf4j
@Configuration @EnableScheduling @Profile("!test & !mock") @RequiredArgsConstructor
public class QuizTimeoutScheduler {
    private final QuizService quizzes;
    @Scheduled(fixedDelayString="${app.quiz.timeout-poll-ms:1000}")
    public void expireDueAttempts(){
        for(var id:quizzes.dueAttempts(100)) {
            try { quizzes.expireOne(id); }
            catch(RuntimeException failure) { log.error("QUIZ_TIMEOUT_FAILED attemptId={}",id); }
        }
    }
}
