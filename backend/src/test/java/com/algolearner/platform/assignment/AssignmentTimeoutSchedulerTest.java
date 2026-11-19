package com.algolearner.platform.assignment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class AssignmentTimeoutSchedulerTest {
    private final AssignmentAttemptRepository attempts = mock(AssignmentAttemptRepository.class);
    private final AssignmentService service = mock(AssignmentService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    private final AssignmentTimeoutScheduler scheduler = new AssignmentTimeoutScheduler(attempts, service, clock);

    @Test
    void pollsOneBoundedBatchUsingServerClock() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        when(attempts.findExpiredTimedAttemptIds(clock.instant(), 100)).thenReturn(List.of(first, second));

        scheduler.expireTimedOutAttempts();

        verify(attempts).findExpiredTimedAttemptIds(clock.instant(), 100);
        var order = inOrder(service);
        order.verify(service).expireAttempt(first);
        order.verify(service).expireAttempt(second);
        verifyNoMoreInteractions(service);
    }

    @Test
    void failureForOneAttemptDoesNotBlockTheRemainingCandidates() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        when(attempts.findExpiredTimedAttemptIds(clock.instant(), 100)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("Test failure")).when(service).expireAttempt(first);

        scheduler.expireTimedOutAttempts();

        verify(service).expireAttempt(first);
        verify(service).expireAttempt(second);
    }

    @Test
    void nextPollRetriesAnAttemptThatPreviouslyFailed() {
        var attemptId = UUID.randomUUID();
        when(attempts.findExpiredTimedAttemptIds(clock.instant(), 100)).thenReturn(List.of(attemptId));
        doThrow(new IllegalStateException("Test failure")).doNothing().when(service).expireAttempt(attemptId);

        scheduler.expireTimedOutAttempts();
        scheduler.expireTimedOutAttempts();

        verify(attempts, times(2)).findExpiredTimedAttemptIds(clock.instant(), 100);
        verify(service, times(2)).expireAttempt(attemptId);
    }

    @Test
    void emptyBatchDoesNotStartAnyAttemptTransaction() {
        when(attempts.findExpiredTimedAttemptIds(clock.instant(), 100)).thenReturn(List.of());

        scheduler.expireTimedOutAttempts();

        verifyNoInteractions(service);
    }
}
