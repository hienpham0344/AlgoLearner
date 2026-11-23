package com.algolearner.platform.assignment;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/admin/classrooms/{classId}/assignments/{assignmentId}")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name="bearerAuth")
public class AssignmentAdministrationController {
    public record Rejudge(@NotBlank @Size(max=500) String reason, AssignmentDtos.RejudgeTarget target) { }
    private final AssignmentService service;
    public AssignmentAdministrationController(AssignmentService service){this.service=service;}

    @PostMapping("/rejudge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Rejudge assignment as active Admin",description="Separate administration context. Requires an active writable classroom and reason; preserves immutable results and actual actor audit.")
    public void rejudge(Authentication actor,@PathVariable UUID classId,@PathVariable UUID assignmentId,@Valid @RequestBody Rejudge request) {
        service.adminRejudge(UUID.fromString(actor.getName()),classId,assignmentId,new AssignmentDtos.RejudgeRequest(request.reason(),request.target()));
    }

    @GetMapping("/submissions/{attemptId}/evaluations")
    @Operation(summary="Read assignment evaluation history as active Admin",description="Read access remains available for archived classrooms. Assignment and attempt scope are both checked.")
    public ApiResponse<List<AssignmentEvaluationHistory.Evaluation>> history(Authentication actor,@PathVariable UUID classId,
            @PathVariable UUID assignmentId,@PathVariable UUID attemptId) {
        return ApiResponse.success(service.adminEvaluationHistory(UUID.fromString(actor.getName()),classId,assignmentId,attemptId));
    }

    @PatchMapping("/questions/{questionId}/code-case-weights")
    @Operation(summary="Adjust working testcase weights as active Admin",description="Changes only the active working question; existing published revisions and attempts remain immutable. Teacher owner must publish a new revision for future attempts.")
    public ApiResponse<AssignmentDtos.CodeCaseWeightsView> weights(Authentication actor,@PathVariable UUID classId,@PathVariable UUID assignmentId,
            @PathVariable UUID questionId,@Valid @RequestBody AssignmentDtos.CodeCaseWeightsRequest request) {
        return ApiResponse.success(service.adminCodeCaseWeights(UUID.fromString(actor.getName()),classId,assignmentId,questionId,request));
    }
}
