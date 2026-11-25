package com.algolearner.platform.assignment;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!mock & !test")
public class AssignmentTimeoutScheduler {
    private static final Logger log = LoggerFactory.getLogger(AssignmentTimeoutScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final AssignmentAttemptRepository attempts;
    private final AssignmentService service;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private AssignmentRepository assignments;

    public AssignmentTimeoutScheduler(AssignmentAttemptRepository attempts, AssignmentService service, Clock clock) {
        this.attempts = attempts;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.assignment.timeout-poll-ms:1000}")
    public void expireTimedOutAttempts() {
        for (var attemptId : attempts.findExpiredTimedAttemptIds(clock.instant(), BATCH_SIZE)) {
            try {
                service.expireAttempt(attemptId);
            } catch (RuntimeException failure) {
                log.warn("Could not expire assignment attempt {} ({}); the next poll will retry",
                        attemptId, failure.getClass().getSimpleName());
            }
        }
    }
    @Scheduled(fixedDelayString = "${app.assignment.judge-poll-ms:1000}")
    public void refreshCodeResults() {
        for(var id:attempts.findPendingOfficialAttemptIds()) {
            try { service.refreshCodeResults(id); }
            catch(RuntimeException failure){log.warn("Could not refresh assignment attempt {} ({})",id,failure.getClass().getSimpleName());}
        }
    }
    @Scheduled(fixedDelayString = "${app.assignment.grade-poll-ms:1000}")
    public void releaseDueGrades() {
        if(assignments==null)return;
        for(var id:assignments.findDuePublicationIds(clock.instant())) {
            try {service.publishDueGrades(id);}
            catch(RuntimeException failure){log.warn("Could not publish assignment grades {} ({})",id,failure.getClass().getSimpleName());}
        }
    }

    @Scheduled(fixedDelayString = "${app.assignment.schedule-poll-ms:1000}")
    public void activateScheduledAssignments() {
        if (assignments == null) return;
        for (var id : assignments.findDueScheduledIds(clock.instant())) {
            try { service.activateScheduledAssignment(id); }
            catch (RuntimeException failure) {
                log.warn("Could not activate scheduled assignment {} ({})", id, failure.getClass().getSimpleName());
            }
        }
    }
}
