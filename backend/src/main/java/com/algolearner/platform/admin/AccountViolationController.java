package com.algolearner.platform.admin;

import com.algolearner.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/admin/accounts/{accountId}/violations")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name="bearerAuth")
public class AccountViolationController {
    public record Confirm(@NotNull UUID requestId,@NotBlank @Size(max=500) String reason) { }
    public record Revoke(@NotBlank @Size(max=500) String reason) { }
    private final AccountViolationService service;
    public AccountViolationController(AccountViolationService service){this.service=service;}
    @PostMapping
    @Operation(summary="Confirm account violation",description="Admin-confirmed evidence only. requestId is idempotent per acting Admin; confirmations and audit are atomic.")
    public ApiResponse<AccountViolationService.View> confirm(Authentication actor,@PathVariable UUID accountId,@Valid @RequestBody Confirm request) {
        return ApiResponse.success(service.confirm(UUID.fromString(actor.getName()),accountId,request.requestId(),request.reason()));
    }
    @PostMapping("/{violationId}/revoke")
    @Operation(summary="Revoke confirmed violation",description="Retains the original confirmation and an atomic audit event; repeated revocation is idempotent.")
    public ApiResponse<AccountViolationService.View> revoke(Authentication actor,@PathVariable UUID accountId,@PathVariable UUID violationId,@Valid @RequestBody Revoke request) {
        return ApiResponse.success(service.revoke(UUID.fromString(actor.getName()),accountId,violationId,request.reason()));
    }
}
