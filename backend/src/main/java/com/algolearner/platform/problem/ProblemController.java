package com.algolearner.platform.problem;

import com.algolearner.platform.common.dto.*;
import com.algolearner.platform.judge.JudgeGateway.EvaluationResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/problems")
@SecurityRequirement(name = "bearerAuth")
public class ProblemController {
    private final ProblemService service;
    private final ProblemCatalogService catalog;

    public ProblemController(ProblemService service, ProblemCatalogService catalog) {
        this.service = service;
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "Browse published public problems")
    public PageResponse<ProblemDtos.CatalogItem> browse(
            Authentication auth,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProblemDtos.Difficulty difficulty,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "NEWEST") ProblemDtos.Order order,
            @PageableDefault(size = 20) Pageable page) {
        return catalog.browse(actor(auth), q, difficulty, tag, order, page);
    }

    @GetMapping("/saved/me")
    @Operation(summary = "List my saved problems")
    public PageResponse<ProblemDtos.CatalogItem> saved(Authentication auth, Pageable page) {
        return catalog.saved(actor(auth), page);
    }

    @PutMapping("/{id}/saved")
    @Operation(summary = "Save a problem to my list")
    public ApiResponse<Void> save(Authentication auth, @PathVariable UUID id) {
        catalog.save(actor(auth), id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/saved")
    @Operation(summary = "Remove a problem from my list")
    public ApiResponse<Void> unsave(Authentication auth, @PathVariable UUID id) {
        catalog.unsave(actor(auth), id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/editorial")
    @Operation(summary = "Read the official editorial")
    public ApiResponse<String> editorial(Authentication auth, @PathVariable UUID id) {
        return ApiResponse.success(catalog.editorial(actor(auth), id));
    }

    @PostMapping(value = "/{id}/reports", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a public problem")
    public ApiResponse<UUID> report(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody ProblemDtos.ReportRequest request) {
        return ApiResponse.success(catalog.report(actor(auth), id, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read a published problem and sample testcases")
    public ApiResponse<ProblemService.View> detail(Authentication auth, @PathVariable UUID id) {
        return ApiResponse.success(service.detail(actor(auth), id));
    }

    @GetMapping("/contributions/me")
    @Operation(summary = "Teacher reads own contribution queue")
    public PageResponse<ProblemService.Summary> contributions(
            Authentication auth, @PageableDefault(size = 20) Pageable page) {
        return service.contributions(actor(auth), false, page);
    }

    @GetMapping("/{id}/contribution")
    @Operation(summary = "Author reads editable content including own configured hidden tests")
    public ApiResponse<ProblemService.Contribution> contribution(
            Authentication auth, @PathVariable UUID id) {
        return ApiResponse.success(service.contribution(actor(auth), id, false));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Teacher creates a draft contribution")
    public ApiResponse<UUID> create(
            Authentication auth, @Valid @RequestBody ProblemService.Content content) {
        return ApiResponse.success(service.create(actor(auth), content));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Author edits draft or rejected contribution")
    public ApiResponse<Void> edit(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody ProblemService.Content content) {
        service.edit(actor(auth), id, content);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/submit-review")
    @Operation(summary = "Author submits draft for Admin review")
    public ApiResponse<Void> submitReview(Authentication auth, @PathVariable UUID id) {
        service.submitReview(actor(auth), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/run")
    @Operation(summary = "Queue sample-only RUN; rolling quota five per minute")
    public ApiResponse<UUID> run(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody ProblemService.CodeInput input) {
        return ApiResponse.success(service.evaluate(actor(auth), id, input, true));
    }

    @PostMapping("/{id}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit official source against immutable configured testcases")
    public ApiResponse<UUID> submit(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody ProblemService.CodeInput input) {
        return ApiResponse.success(service.evaluate(actor(auth), id, input, false));
    }

    @GetMapping("/{id}/submissions")
    @Operation(summary = "Read only the caller's official submission history")
    public PageResponse<ProblemService.Submission> history(
            Authentication auth, @PathVariable UUID id, @PageableDefault(size = 20) Pageable page) {
        return service.history(actor(auth), id, page);
    }

    @GetMapping("/{id}/submissions/{submission}")
    @Operation(summary = "Read my source, revision and rejudge history")
    public ApiResponse<ProblemService.SubmissionDetail> submission(
            Authentication auth, @PathVariable UUID id, @PathVariable UUID submission) {
        return ApiResponse.success(service.submission(actor(auth), id, submission));
    }

    @GetMapping("/{id}/jobs/{job}")
    @Operation(summary = "Read caller's scoped judge result under configured feedback")
    public ApiResponse<EvaluationResult> result(
            Authentication auth, @PathVariable UUID id, @PathVariable UUID job) {
        return ApiResponse.success(service.result(actor(auth), id, job));
    }

    @GetMapping("/statistics/me")
    @Operation(summary = "Read solved count and UTC+7 streak from official accepted results")
    public ApiResponse<ProblemService.Statistics> stats(Authentication auth) {
        return ApiResponse.success(service.statistics(actor(auth)));
    }

    private static UUID actor(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
