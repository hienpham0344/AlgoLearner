package com.algolearner.platform.common.config;

import com.algolearner.platform.common.dto.ApiResponse;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!mock")
public class StatusController {
    @GetMapping("/api/v1/status")
    public ResponseEntity<ApiResponse<Map<String, String>>> status() {
        return ResponseEntity.ok()
                .header("X-Algolearner-Source", "backend")
                .body(ApiResponse.success(Map.of("source", "backend")));
    }
}
