package com.algolearner.platform.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!mock")
public class JudgeQueueRecovery {
    private static final Logger LOG = LoggerFactory.getLogger(JudgeQueueRecovery.class);
    private final SubmissionEvaluationRepository evaluations;
    private final RejudgeHistoryRepository rejudges;
    private final JudgeSubmissionProcessor processor;
    private final int batchSize;

    public JudgeQueueRecovery(SubmissionEvaluationRepository evaluations, RejudgeHistoryRepository rejudges,
            JudgeSubmissionProcessor processor, @Value("${app.judge.recovery-batch-size:20}") int batchSize) {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("Judge recovery batch must be 1..100");
        this.evaluations = evaluations;
        this.rejudges = rejudges;
        this.processor = processor;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.judge.recovery-delay-ms:30000}")
    public void recoverQueuedWork() {
        var page = PageRequest.of(0, batchSize);
        for (var evaluationId : evaluations.findQueuedInitialIds(page)) {
            try { processor.processSubmission(evaluationId); }
            catch (RuntimeException exception) { LOG.warn("Judge queue recovery failed: evaluationId={}", evaluationId); }
        }
        for (var historyId : rejudges.findQueuedIds(page)) {
            try { processor.processRejudge(historyId); }
            catch (RuntimeException exception) { LOG.warn("Rejudge queue recovery failed: historyId={}", historyId); }
        }
    }
}
