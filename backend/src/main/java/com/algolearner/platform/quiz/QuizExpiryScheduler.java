package com.algolearner.platform.quiz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;

@Configuration
@Profile("!mock")
@EnableScheduling
public class QuizExpiryScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(QuizExpiryScheduler.class);
    private final QuizAttemptRepository attempts;
    private final QuizService service;
    private final Clock clock;
    public QuizExpiryScheduler(QuizAttemptRepository attempts, QuizService service, Clock clock) {
        this.attempts=attempts; this.service=service; this.clock=clock;
    }
    @Scheduled(fixedDelayString="${app.quiz.expiry-interval-ms:1000}")
    public void expire() {
        for(var a:attempts.findByStatusAndDeadlineLessThanEqual("ACTIVE",clock.instant(),PageRequest.of(0,100))) {
            try { service.expireAttempt(a.getId()); }
            catch(RuntimeException ex) { LOG.error("Could not expire quiz attempt {}",a.getId(),ex); }
        }
    }
}
