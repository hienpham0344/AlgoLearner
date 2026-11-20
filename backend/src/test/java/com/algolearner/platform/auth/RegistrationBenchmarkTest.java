package com.algolearner.platform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in benchmark; all accounts live only in the isolated test schema dropped at JVM shutdown. */
@SpringBootTest(properties = {
    "app.security.jwt-secret=registration-benchmark-test-only-secret-at-least-32-bytes",
    "app.mail.from=sender@example.test"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
@EnabledIfSystemProperty(named = "registration.benchmark", matches = "true")
class RegistrationBenchmarkTest extends com.algolearner.platform.support.PostgresIntegrationTest {
    private static final int WARMUP = 5;
    private static final int SAMPLES = 30;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean JavaMailSender sender;

    @Test
    void measureRegistrationStagesWithMockSmtp() throws Exception {
        Map<String, List<Long>> samples = new LinkedHashMap<>();
        for (int attempt = 0; attempt < WARMUP + SAMPLES; attempt++) {
            String marker = UUID.randomUUID().toString().replace("-", "");
            String body = json.writeValueAsString(Map.of(
                    "email", "codexperf-" + marker + "@example.test",
                    "username", "perf" + marker,
                    "password", "ExamplePass123!",
                    "confirmPassword", "ExamplePass123!",
                    "role", "STUDENT",
                    "termsAccepted", true,
                    "privacyAccepted", true));
            long started = System.nanoTime();
            Map<String, Long> stages;
            try (var capture = RegistrationTiming.capture()) {
                mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isCreated());
                stages = capture.snapshot();
            }
            long total = System.nanoTime() - started;
            if (attempt < WARMUP) continue;
            samples.computeIfAbsent("http_total", ignored -> new ArrayList<>()).add(total);
            stages.forEach((stage, duration) -> samples.computeIfAbsent(stage, ignored -> new ArrayList<>())
                    .add(duration));
        }
        verify(sender, times(WARMUP + SAMPLES)).send(any(SimpleMailMessage.class));
        for (var entry : samples.entrySet()) {
            assertEquals(SAMPLES, entry.getValue().size(), "Missing stage: " + entry.getKey());
            System.out.printf("REGISTRATION_BENCH stage=%s count=%d p50_ms=%.2f p95_ms=%.2f%n",
                    entry.getKey(), SAMPLES, percentileMs(entry.getValue(), 0.50),
                    percentileMs(entry.getValue(), 0.95));
        }
    }

    private static double percentileMs(List<Long> nanos, double percentile) {
        var sorted = new ArrayList<>(nanos);
        sorted.sort(Long::compare);
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index) / 1_000_000.0;
    }
}
