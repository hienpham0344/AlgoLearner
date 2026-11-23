package com.algolearner.platform.auth;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-request registration timings used only when an opt-in benchmark captures the current thread. */
final class RegistrationTiming {
    private static final ThreadLocal<Map<String, Long>> ACTIVE = new ThreadLocal<>();

    private RegistrationTiming() { }

    static long begin() {
        return ACTIVE.get() == null ? 0L : System.nanoTime();
    }

    static void record(String stage, long startedAt) {
        if (startedAt == 0L) return;
        Map<String, Long> durations = ACTIVE.get();
        if (durations != null) durations.merge(stage, System.nanoTime() - startedAt, Long::sum);
    }

    static Capture capture() {
        if (ACTIVE.get() != null) throw new IllegalStateException("Registration timing is already active");
        Map<String, Long> durations = new LinkedHashMap<>();
        ACTIVE.set(durations);
        return new Capture(durations);
    }

    static final class Capture implements AutoCloseable {
        private final Map<String, Long> durations;

        private Capture(Map<String, Long> durations) {
            this.durations = durations;
        }

        Map<String, Long> snapshot() {
            return Map.copyOf(durations);
        }

        @Override
        public void close() {
            ACTIVE.remove();
        }
    }
}
