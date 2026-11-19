package com.algolearner.platform.judge;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRunRateLimiterTest {
    @Test
    void allowsFiveRunsPerAccountInRollingMinute() {
        InMemoryRunRateLimiter limiter = new InMemoryRunRateLimiter(5);
        UUID actor = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-16T02:00:00Z");

        for (int index = 0; index < 5; index++) {
            assertThat(limiter.tryAcquire(actor, start.plusSeconds(index))).isTrue();
        }
        assertThat(limiter.tryAcquire(actor, start.plusSeconds(59))).isFalse();
        assertThat(limiter.tryAcquire(actor, start.plusSeconds(60))).isTrue();
    }
}
