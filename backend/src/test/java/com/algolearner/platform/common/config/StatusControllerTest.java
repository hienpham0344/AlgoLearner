package com.algolearner.platform.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ForwardedHeaderFilter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatusControllerTest {

    @RestController
    static class SecureProbeController {
        @GetMapping("/probe-secure")
        String secure(HttpServletRequest request) {
            return Boolean.toString(request.isSecure());
        }
    }

    @Test
    void statusReturnsBackendSourceHeaderAndJsonBody() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new StatusController()).build();
        mvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Algolearner-Source", "backend"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.source").value("backend"));
    }

    @Test
    void applicationYmlConfiguresFrameworkForwardedHeaderStrategy() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains("forward-headers-strategy: framework");
    }

    @Test
    void forwardedHeaderFilterMarksProxiedHttpsRequestsAsSecure() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new SecureProbeController())
                .addFilters(new ForwardedHeaderFilter())
                .build();
        mvc.perform(get("/probe-secure").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
        mvc.perform(get("/probe-secure"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
}
