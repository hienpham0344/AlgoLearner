package com.algolearner.platform.judge;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!mock")
public class JudgeQueueListener {
    private final JudgeSubmissionProcessor processor;

    public JudgeQueueListener(JudgeSubmissionProcessor processor) { this.processor = processor; }

    @Async("judgeTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void submissionQueued(SubmissionQueuedEvent event) {
        processor.processSubmission(event.evaluationId());
    }

    @Async("judgeTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void rejudgeQueued(RejudgeQueuedEvent event) {
        processor.processRejudge(event.historyId());
    }
}
