package com.algolearner.platform.judge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRunRateLimiter implements RunRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final ConcurrentHashMap<UUID, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final int permits;

    public InMemoryRunRateLimiter(@Value("${app.judge.run-limit-per-minute:5}") int permits) {
        if (permits < 1 || permits > 5) throw new IllegalArgumentException("RUN rate limit must be between 1 and 5");
        this.permits = permits;
    }

    @Override
    public boolean tryAcquire(UUID accountId, Instant now) {
        final boolean[] accepted = {false};
        attempts.compute(accountId, (ignored, existing) -> {
            Deque<Instant> window = existing == null ? new ArrayDeque<>() : existing;
            Instant cutoff = now.minus(WINDOW);
            while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) window.removeFirst();
            if (window.size() < permits) {
                window.addLast(now);
                accepted[0] = true;
            }
            return window;
        });
        return accepted[0];
    }
}
