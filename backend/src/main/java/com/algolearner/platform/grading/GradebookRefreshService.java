package com.algolearner.platform.grading;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Slf4j
public class GradebookRefreshService {
    private final GradebookRefreshRepository queue;
    private final GradebookSchemeService schemes;
    private final GradebookPublicationRepository publications;
    private final GradebookPublicationService publisher;
    private final GradebookHistoryService history;
    private final PlatformTransactionManager transactions;
    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_BACKOFF_SECONDS = 300;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void sourceChanged(GradeSourceChanged event) {
        queue.enqueue(event);
    }

    void requestSnapshotRefresh(GradebookQueryService.StaleSnapshot snapshot) {
        queue.enqueueStaleSnapshot(snapshot.gradebookId(), snapshot.studentId(), snapshot.resultId());
    }

    public void process(GradebookRefreshRepository.Work work) {
        processOne(work);
    }

    boolean processNext() {
        return processOne(null);
    }

    private boolean processOne(GradebookRefreshRepository.Work requested) {
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var claim = new java.util.concurrent.atomic.AtomicReference<GradebookRefreshRepository.Claimed>();
        try {
            return Boolean.TRUE.equals(transaction.execute(status -> {
                var selected = queue.claim(requested);
                if (selected.isEmpty()) return false;
                claim.set(selected.get());
                processClaim(selected.get());
                return true;
            }));
        } catch (RuntimeException failure) {
            var selected = claim.get();
            if (selected == null) throw failure;
            int attempts = selected.attempts() + 1;
            boolean terminal = attempts >= MAX_ATTEMPTS;
            int delaySeconds = Math.min(MAX_BACKOFF_SECONDS, 5 * (1 << Math.min(attempts - 1, 6)));
            if (Boolean.TRUE.equals(transaction.execute(status -> queue.recordFailure(selected, delaySeconds, terminal)))) {
                log.warn("Gradebook refresh failure gradebookId={} attempt={} terminal={} terminalFailureCount={}",
                        selected.work().gradebookId(), attempts, terminal, terminal ? 1 : 0);
            }
            return true;
        }
    }

    private void processClaim(GradebookRefreshRepository.Claimed claimed) {
        var work = claimed.work();
        var gradebook = schemes.lock(work.classroomId());
        if (!queue.lockWork(claimed)) return;
        var previous = publications.current(gradebook.getId(), work.studentId());
        if (previous.isPresent() && queue.activeMember(work)) {
            var scheme = schemes.active(gradebook);
            var updated = publisher.calculate(work.classroomId(), scheme, List.of(work.studentId())).get(work.studentId());
            var old = previous.get();
            if (!old.schemeId().equals(scheme.revisionId()) || !old.overall().equals(updated.overall())
                    || !updated.contributors().equals(old.contributors())) {
                history.sourceRecalculated(work.classroomId(), gradebook.getId(), work.studentId(), old, scheme.revisionId(), updated);
            }
        }
        queue.complete(work);
    }
}
