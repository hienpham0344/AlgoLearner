package com.algolearner.platform.auth;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("internal-auth")
public class InternalProfileController {
    private final AuthService service;
    public InternalProfileController(AuthService service) { this.service = service; }

    @Operation(summary = "Read the authenticated account for session restore")
    @GetMapping("/me")
    public ApiResponse<AuthDtos.AuthUser> me(Authentication authentication) {
        return ApiResponse.success(service.me(UUID.fromString(authentication.getName())));
    }
}
