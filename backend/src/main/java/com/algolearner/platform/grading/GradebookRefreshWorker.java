package com.algolearner.platform.grading;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Component
@Profile("!mock & (!test | gradebook-worker-test)")
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class GradebookRefreshWorker {
    private final GradebookRefreshService refresh;

    @Value("${app.gradebook.refresh.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.gradebook.refresh.delay-ms:2000}")
    public void refreshDueGrades() {
        try {
            for (int i = 0; i < Math.max(1, batchSize); i++) {
                if (!refresh.processNext()) break;
            }
        } catch (RuntimeException failure) {
            log.warn("Gradebook refresh queue unavailable");
        }
    }
}