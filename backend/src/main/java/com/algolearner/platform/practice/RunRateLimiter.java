package com.algolearner.platform.practice;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter for RUN: at most {@value #LIMIT} runs per user per minute (SRS 4.8).
 * In-process, so the limit is per application instance.
 */
@Component
public class RunRateLimiter {
    static final int LIMIT = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);
    private final Map<UUID, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RunRateLimiter(Clock clock) { this.clock = clock; }

    public void acquire(UUID userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        boolean[] allowed = {true};
        hits.compute(userId, (id, window) -> {
            Deque<Instant> deque = window == null ? new ArrayDeque<>() : window;
            while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) deque.pollFirst();
            if (deque.size() >= LIMIT) allowed[0] = false;
            else deque.addLast(now);
            return deque;
        });
        if (!allowed[0]) throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, "RUN is limited to 5 times per minute");
    }
}
