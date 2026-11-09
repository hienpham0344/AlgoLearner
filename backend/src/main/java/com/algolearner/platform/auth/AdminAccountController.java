package com.algolearner.platform.auth;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.algolearner.platform.auth.AuthDtos.MessageResponse;
import static com.algolearner.platform.auth.AuthDtos.SuspendAccountRequest;

@RestController
@RequestMapping(value = "/api/v1/admin/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin Account Management", description = "FR-ACC-006: Admin account lifecycle management, suspensions, and reactivation")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminAccountController {

    private final AuthService authService;

    public AdminAccountController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/{accountId}/suspend")
    @Operation(summary = "Suspend a user account with required reason (FR-ACC-006)")
    public ResponseEntity<ApiResponse<MessageResponse>> suspend(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody SuspendAccountRequest request
    ) {
        UUID adminId = id(authentication);
        MessageResponse response = authService.suspendAccount(adminId, accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @PostMapping("/{accountId}/unsuspend")
    @Operation(summary = "Lift account suspension (FR-ACC-006)")
    public ResponseEntity<ApiResponse<MessageResponse>> unsuspend(
            Authentication authentication,
            @PathVariable UUID accountId
    ) {
        UUID adminId = id(authentication);
        MessageResponse response = authService.unsuspendAccount(adminId, accountId);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    @PostMapping("/{accountId}/reactivate")
    @Operation(summary = "Reactivate a deactivated account (FR-ACC-006)")
    public ResponseEntity<ApiResponse<MessageResponse>> reactivate(
            Authentication authentication,
            @PathVariable UUID accountId
    ) {
        UUID adminId = id(authentication);
        MessageResponse response = authService.reactivateAccount(adminId, accountId);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }

    private UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
