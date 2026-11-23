package com.algolearner.platform.storage;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/admin/file-policy")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Upload policy administration")
@SecurityRequirement(name = "bearerAuth")
public class FilePolicyController {
    private final FilePolicyService service;
    public record Update(@NotNull FilePolicyService.Policy policy, @NotBlank @Size(max = 500) String reason) { }

    @GetMapping
    @Operation(summary = "Read upload policy", description = "Admin only. Limits are bounded by the application's 20 MiB ceiling and provider bucket settings.")
    public ApiResponse<FilePolicyService.Policy> read(Authentication authentication) {
        return ApiResponse.success(service.read(UUID.fromString(authentication.getName())));
    }

    @PutMapping
    @Operation(summary = "Replace upload policy", description = "Admin only; requires a reason and records the change atomically in audit. Existing stored files remain readable; new uploads use the new policy.")
    public ApiResponse<FilePolicyService.Policy> update(Authentication authentication, @Valid @RequestBody Update request) {
        return ApiResponse.success(service.update(UUID.fromString(authentication.getName()), request.policy(), request.reason()));
    }
}
