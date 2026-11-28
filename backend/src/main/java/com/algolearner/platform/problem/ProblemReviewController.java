package com.algolearner.platform.problem;

import com.algolearner.platform.common.dto.ApiResponse;
import com.algolearner.platform.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/admin/problems")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class ProblemReviewController {
    public record Review(
            Boolean approve,
            ProblemDtos.ReviewDecision decision,
            @Size(max = 2000) String reason,
            Boolean publish) {}

    private final ProblemService service;
    private final ProblemCatalogService catalog;

    public ProblemReviewController(ProblemService service, ProblemCatalogService catalog) {
        this.service = service;
        this.catalog = catalog;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft as administrator")
    public ApiResponse<UUID> create(
            Authentication auth, @Valid @RequestBody ProblemService.Content request) {
        return ApiResponse.success(service.create(UUID.fromString(auth.getName()), request));
    }

    @PutMapping("/{id}/content")
    @Operation(summary = "Update problem content")
    public ApiResponse<Void> edit(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody ProblemService.Content request) {
        service.adminEdit(UUID.fromString(auth.getName()), id, request);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/publication")
    @Operation(summary = "Publish, unpublish or archive a problem")
    public ApiResponse<Void> publication(
            Authentication auth,
            @PathVariable UUID id,
            @RequestParam ProblemDtos.Publication status) {
        service.publication(UUID.fromString(auth.getName()), id, status);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a problem without dependent learning data")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable UUID id) {
        service.delete(UUID.fromString(auth.getName()), id);
        return ApiResponse.success(null);
    }

    @GetMapping("/reports")
    @Operation(summary = "List problem reports")
    public PageResponse<ProblemDtos.Report> reports(Authentication auth, Pageable page) {
        return catalog.reports(UUID.fromString(auth.getName()), page);
    }

    @PutMapping("/reports/{id}/status")
    @Operation(summary = "Update report status")
    public ApiResponse<Void> reportStatus(
            Authentication auth,
            @PathVariable UUID id,
            @RequestParam ProblemDtos.ReportStatus status) {
        catalog.updateReport(UUID.fromString(auth.getName()), id, status);
        return ApiResponse.success(null);
    }

    @GetMapping
    @Operation(summary = "Admin reads pending contribution review queue")
    public PageResponse<ProblemService.Summary> queue(Authentication auth, @PageableDefault(size = 20) Pageable page) {
        return service.contributions(UUID.fromString(auth.getName()), true, page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Admin inspects full contributed content before review")
    public ApiResponse<ProblemService.Contribution> detail(
            Authentication auth, @PathVariable UUID id) {
        return ApiResponse.success(service.contribution(UUID.fromString(auth.getName()), id, true));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Admin approves publication or rejects with mandatory reason")
    public ApiResponse<Void> review(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody Review request) {
        var decision = request.decision();
        if (decision == null && request.approve() == null)
            throw ProblemService.failure(HttpStatus.BAD_REQUEST, "REVIEW_DECISION_REQUIRED");
        if (decision == null)
            decision =
                    request.approve()
                            ? ProblemDtos.ReviewDecision.APPROVE
                            : ProblemDtos.ReviewDecision.REJECT;
        boolean publish =
                request.publish() != null ? request.publish() : request.decision() == null;
        service.review(UUID.fromString(auth.getName()), id, decision, request.reason(), publish);
        return ApiResponse.success(null);
    }
}
