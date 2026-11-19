package com.algolearner.platform.judge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.algolearner.platform.judge.JudgeDtos.*;
import static com.algolearner.platform.judge.JudgeTypes.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JudgeController.class)
@AutoConfigureMockMvc(addFilters = false)
class JudgeApiTest {
    @Autowired MockMvc mvc;
    @MockBean JudgeService service;

    @Test
    void runReturnsStandardEnvelope() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        when(service.run(eq(actorId), any())).thenReturn(new RunResponse(JudgeStatus.ACCEPTED,
                "3", null, 10, 32, List.of(new CaseResult(1, JudgeStatus.ACCEPTED, 10L, 32L, "3", null))));

        mvc.perform(post("/api/v1/judge/run").principal(actor(actorId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":\"" + problemId + "\",\"language\":\"JAVA\",\"sourceCode\":\"class Main {}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.cases[0].ordinalNumber").value(1));
    }

    @Test
    void submitReturnsAcceptedQueuedResponseWithoutEchoingSourceCode() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        when(service.submit(eq(actorId), any())).thenReturn(new SubmissionResponse(submissionId,
                problemId, actorId, UUID.randomUUID(), JudgeLanguage.PYTHON, JudgeStatus.QUEUED,
                null, null, null, null, null, Instant.parse("2026-11-16T02:00:00Z"), List.of()));

        mvc.perform(post("/api/v1/judge/submit").principal(actor(actorId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":\"" + problemId + "\",\"language\":\"PYTHON\",\"sourceCode\":\"print(1)\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(submissionId.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.sourceCode").doesNotExist());
    }

    @Test
    void invalidRunPayloadUsesValidationErrorEnvelope() throws Exception {
        mvc.perform(post("/api/v1/judge/run").principal(actor(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"RUST\",\"sourceCode\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void updateProblemAcceptsBoundedResourceAndFeedbackChanges() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        when(service.updateProblem(eq(actorId), eq(problemId), any())).thenReturn(new ProblemResponse(problemId,
                actorId, JudgeContextType.CLASS_ASSIGNMENT, UUID.randomUUID(), UUID.randomUUID(), "Code task",
                JudgeProblemFormat.STANDARD_IO, 3_000, 384, JudgeFeedbackLevel.DETAILED,
                java.util.Set.of(JudgeLanguage.JAVA), null, null, true, null, null));

        mvc.perform(patch("/api/v1/judge/problems/{id}", problemId).principal(actor(actorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeLimitMs\":3000,\"memoryLimitMb\":384,\"feedbackLevel\":\"DETAILED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeLimitMs").value(3000))
                .andExpect(jsonPath("$.data.feedbackLevel").value("DETAILED"));
    }

    @Test
    void getProblemReturnsConfig() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        when(service.getProblem(eq(actorId), eq(problemId))).thenReturn(new ProblemResponse(problemId,
                actorId, JudgeContextType.PUBLIC_PROBLEM, UUID.randomUUID(), null, "Two Sum",
                JudgeProblemFormat.STANDARD_IO, 2_000, 256, JudgeFeedbackLevel.TEST_STATUS,
                java.util.Set.of(JudgeLanguage.JAVA, JudgeLanguage.PYTHON), null, null, true, null, null));

        mvc.perform(get("/api/v1/judge/problems/{id}", problemId).principal(actor(actorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(problemId.toString()))
                .andExpect(jsonPath("$.data.title").value("Two Sum"));
    }

    @Test
    void getSampleTestCasesReturnsSamplesOnly() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        when(service.getSampleTestCases(eq(actorId), eq(problemId))).thenReturn(List.of(
                new SampleTestCaseResponse(1, "1 2", "3")));

        mvc.perform(get("/api/v1/judge/problems/{id}/sample-test-cases", problemId).principal(actor(actorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ordinalNumber").value(1))
                .andExpect(jsonPath("$.data[0].input").value("1 2"))
                .andExpect(jsonPath("$.data[0].expectedOutput").value("3"));
    }

    private static UsernamePasswordAuthenticationToken actor(UUID actorId) {
        return new UsernamePasswordAuthenticationToken(actorId.toString(), "n/a", List.of());
    }
}
