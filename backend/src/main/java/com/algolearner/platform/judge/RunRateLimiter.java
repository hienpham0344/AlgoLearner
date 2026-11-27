package com.algolearner.platform.judge;

import java.time.Instant;
import java.util.UUID;

public interface RunRateLimiter {
    boolean tryAcquire(UUID accountId, Instant now);
}
