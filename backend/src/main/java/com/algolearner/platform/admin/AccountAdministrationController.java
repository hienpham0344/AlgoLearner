package com.algolearner.platform.admin;

import com.algolearner.platform.common.dto.ApiResponse;
import com.algolearner.platform.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import static com.algolearner.platform.admin.AccountAdminDtos.*;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@Profile("internal-auth")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AccountAdministrationController {
    private final AccountAdministrationService service;

    public AccountAdministrationController(AccountAdministrationService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "List accounts for moderation")
    public PageResponse<AccountView> list(Authentication auth, @PageableDefault(size = 20, sort = "createdAt",
            direction = Sort.Direction.DESC) Pageable pageable) { return service.list(actor(auth), pageable); }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend an active Student or Teacher with a reason and optional end time")
    public ApiResponse<AccountView> suspend(Authentication auth, @PathVariable UUID id,
            @Valid @RequestBody SuspendRequest input) {
        return ApiResponse.success(service.suspend(actor(auth), id, input));
    }

    @PostMapping("/{id}/unsuspend")
    @Operation(summary = "Remove an account suspension")
    public ApiResponse<AccountView> unsuspend(Authentication auth, @PathVariable UUID id) {
        return ApiResponse.success(service.unsuspend(actor(auth), id));
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reactivate a deactivated Student or Teacher account")
    public ApiResponse<AccountView> reactivate(Authentication auth, @PathVariable UUID id) {
        return ApiResponse.success(service.reactivate(actor(auth), id));
    }

    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }
}
