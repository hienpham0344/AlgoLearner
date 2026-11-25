package com.algolearner.platform.problem;
import java.util.UUID;
import com.algolearner.platform.common.dto.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@RestController @Profile("!mock") @RequestMapping("/api/v1/admin/problems") @SecurityRequirement(name="bearerAuth")
public class ProblemReviewController {
    public record Review(boolean approve,@Size(max=2000) String reason) { }
    private final ProblemService service;public ProblemReviewController(ProblemService service){this.service=service;}
    @GetMapping @Operation(summary="Admin reads pending contribution review queue")
    public com.algolearner.platform.common.dto.PageResponse<ProblemService.Summary> queue(Authentication auth,@org.springframework.data.web.PageableDefault(size=20) org.springframework.data.domain.Pageable page){return service.contributions(UUID.fromString(auth.getName()),true,page);}
    @GetMapping("/{id}") @Operation(summary="Admin inspects full contributed content before review")
    public ApiResponse<ProblemService.Contribution> detail(Authentication auth,@PathVariable UUID id){return ApiResponse.success(service.contribution(UUID.fromString(auth.getName()),id,true));}
    @PostMapping("/{id}/review") @Operation(summary="Admin approves publication or rejects with mandatory reason")
    public ApiResponse<Void> review(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Review request){service.review(UUID.fromString(auth.getName()),id,request.approve(),request.reason());return ApiResponse.success(null);}
}
