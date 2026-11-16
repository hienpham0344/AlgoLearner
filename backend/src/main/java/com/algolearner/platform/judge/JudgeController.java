package com.algolearner.platform.judge;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.algolearner.platform.judge.JudgeDtos.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/judge")
@Tag(name = "Code Judge", description = "SRS 4.9 isolated Java/Python execution, official submissions and rejudge")
@SecurityRequirement(name = "bearerAuth")
public class JudgeController {
    private final JudgeService service;

    public JudgeController(JudgeService service) { this.service = service; }

    @PostMapping("/run")
    @Operation(summary = "Run source against sample or custom input without creating a submission")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Run completed",
                content = @Content(schema = @Schema(implementation = RunResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Problem or language is unavailable"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Five RUN requests per minute exceeded"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Isolated Judge provider unavailable")
    })
    public ApiResponse<RunResponse> run(Authentication authentication, @Valid @RequestBody RunRequest request) {
        return ApiResponse.success(service.run(actor(authentication), request));
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Create an official queued submission using the active full test-case version")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Submission queued",
                content = @Content(schema = @Schema(implementation = SubmissionResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Problem access denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Submission conditions are invalid")
    })
    public ApiResponse<SubmissionResponse> submit(Authentication authentication,
            @Valid @RequestBody SubmitRequest request) {
        return ApiResponse.success("Submission queued", service.submit(actor(authentication), request));
    }

    @GetMapping("/submissions/{submissionId}")
    @Operation(summary = "Read an authorized submission with feedback-policy filtering")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Submission result"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Submission access denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Submission not found")
    })
    public ApiResponse<SubmissionResponse> submission(Authentication authentication,
            @PathVariable UUID submissionId) {
        return ApiResponse.success(service.getSubmission(actor(authentication), submissionId));
    }

    @PostMapping("/submissions/{submissionId}/rejudge")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "Queue an append-only rejudge while preserving the current official result")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Rejudge queued"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Owning teacher or admin required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Submission not found")
    })
    public ApiResponse<RejudgeResponse> rejudge(Authentication authentication, @PathVariable UUID submissionId,
            @Valid @RequestBody(required = false) RejudgeRequest request) {
        return ApiResponse.success("Rejudge queued",
                service.requestRejudge(actor(authentication), submissionId, request == null ? new RejudgeRequest(null) : request));
    }

    @PostMapping("/problems")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "Configure a Java/Python Judge problem")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Problem configured"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Problem management denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Context already has a Judge problem")
    })
    public ApiResponse<ProblemResponse> createProblem(Authentication authentication,
            @Valid @RequestBody ProblemRequest request) {
        return ApiResponse.success("Judge problem created", service.createProblem(actor(authentication), request));
    }

    @PatchMapping("/problems/{problemId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "Update resource limits, feedback, languages or starter code without changing ownership/context")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Problem configuration updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Configuration is outside allowed bounds"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Problem management denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Problem not found")
    })
    public ApiResponse<ProblemResponse> updateProblem(Authentication authentication, @PathVariable UUID problemId,
            @Valid @RequestBody ProblemUpdateRequest request) {
        return ApiResponse.success("Judge problem updated",
                service.updateProblem(actor(authentication), problemId, request));
    }

    @PostMapping("/problems/{problemId}/test-case-versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "Publish a new immutable test-case version and explicitly choose whether to rejudge")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Test-case version published"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Problem management denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Problem not found")
    })
    public ApiResponse<TestCaseVersionResponse> publishTestCases(Authentication authentication,
            @PathVariable UUID problemId, @Valid @RequestBody PublishTestCaseVersionRequest request) {
        return ApiResponse.success("Test-case version published",
                service.publishTestCaseVersion(actor(authentication), problemId, request));
    }

    @GetMapping("/problems/{problemId}")
    @Operation(summary = "Get Judge problem configuration by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Problem details"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Problem access denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Problem not found")
    })
    public ApiResponse<ProblemResponse> getProblem(Authentication authentication, @PathVariable UUID problemId) {
        return ApiResponse.success(service.getProblem(actor(authentication), problemId));
    }

    @GetMapping("/problems/context/{contextType}/{contextId}")
    @Operation(summary = "Get Judge problem configuration by its context")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Problem details"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Problem access denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Problem not found")
    })
    public ApiResponse<ProblemResponse> getProblemByContext(Authentication authentication,
            @PathVariable JudgeTypes.JudgeContextType contextType, @PathVariable UUID contextId) {
        return ApiResponse.success(service.getProblemByContext(actor(authentication), contextType, contextId));
    }

    @GetMapping("/problems/{problemId}/sample-test-cases")
    @Operation(summary = "Get sample test cases visible to learners for running local checks")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sample test cases"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Problem access denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Problem not found")
    })
    public ApiResponse<java.util.List<SampleTestCaseResponse>> getSampleTestCases(Authentication authentication,
            @PathVariable UUID problemId) {
        return ApiResponse.success(service.getSampleTestCases(actor(authentication), problemId));
    }

    private static UUID actor(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
