package com.algolearner.platform.practice;

import com.algolearner.platform.common.exception.JudgeServiceUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fallbacks used until modules 4.9 and 4.5 provide real adapters. */
@Configuration
public class PracticeJudgeConfiguration {

    /** Fails closed: no real judge means 503, never a made-up verdict. */
    @Bean
    @ConditionalOnMissingBean(PracticeJudgePort.class)
    PracticeJudgePort unavailablePracticeJudge() {
        return new PracticeJudgePort() {
            @Override public RunResult run(RunRequest request) { throw unavailable(); }
            @Override public SubmitResult submit(SubmitRequest request) { throw unavailable(); }
            private JudgeServiceUnavailableException unavailable() {
                return new JudgeServiceUnavailableException("Code judge is not configured");
            }
        };
    }

    /** No lesson module yet, so no lesson can be archived. */
    @Bean
    @ConditionalOnMissingBean(LessonStatusPort.class)
    LessonStatusPort noLessonsArchived() {
        return lessonId -> false;
    }
}
