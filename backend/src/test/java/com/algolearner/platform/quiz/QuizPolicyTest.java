package com.algolearner.platform.quiz;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class QuizPolicyTest {
    @Test
    void cutoffIsExclusiveAtServerMicrosecondBoundary() {
        var cutoff=Instant.parse("2026-11-18T00:00:00.123456Z");
        assertThat(QuizPolicy.expired(cutoff.minusNanos(1000),cutoff)).isFalse();
        assertThat(QuizPolicy.expired(cutoff,cutoff)).isTrue();
        assertThat(QuizPolicy.expired(cutoff.plusNanos(1000),cutoff)).isTrue();
    }
    @Test
    void frozenCutoffUsesEarlierDeadlineAndTimerUnlessLateAllowed() {
        var start=Instant.parse("2026-11-18T00:00:00Z");
        assertThat(QuizPolicy.cutoff(start,60)).isEqualTo(start.plusSeconds(60));
        assertThat(QuizPolicy.cutoff(start,null)).isNull();
    }
}
