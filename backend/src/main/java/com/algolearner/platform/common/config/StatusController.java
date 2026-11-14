package com.algolearner.platform.common.config;

import com.algolearner.platform.common.dto.ApiResponse;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("internal-auth")
public class StatusController {
    @GetMapping("/api/v1/status")
    public ApiResponse<Map<String, String>> status() { return ApiResponse.success(Map.of("source", "backend")); }
}
