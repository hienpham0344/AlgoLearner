package com.algolearner.platform.practice;
import java.util.UUID;
import com.algolearner.platform.problem.ProblemService;
import com.algolearner.platform.judge.JudgeGateway.EvaluationResult;
import com.algolearner.platform.common.dto.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@RestController @Profile("!mock") @RequestMapping("/api/v1/classrooms/{classroom}/practice") @SecurityRequirement(name="bearerAuth")
public class ClassPracticeController {
    public record Copy(@NotNull UUID problemId) { }
    private final ClassPracticeService service;public ClassPracticeController(ClassPracticeService service){this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Teacher owner copies a published public problem as an independent class snapshot")
    public ApiResponse<UUID> copy(Authentication auth,@PathVariable UUID classroom,@Valid @RequestBody Copy copy){return ApiResponse.success(service.copy(actor(auth),classroom,copy.problemId()));}
    @PostMapping("/{id}/publish") @Operation(summary="Teacher owner publishes class practice")
    public ApiResponse<Void> publish(Authentication auth,@PathVariable UUID classroom,@PathVariable UUID id){service.publish(actor(auth),classroom,id);return ApiResponse.success(null);}
    @GetMapping @Operation(summary="List authorized class practice, published only for active Students")
    public PageResponse<ProblemService.Summary> list(Authentication auth,@PathVariable UUID classroom,@PageableDefault(size=20) Pageable page){return service.list(actor(auth),classroom,page);}
    @GetMapping("/{id}") @Operation(summary="Read authorized practice without hidden testcase data")
    public ApiResponse<ProblemService.View> detail(Authentication auth,@PathVariable UUID classroom,@PathVariable UUID id){return ApiResponse.success(service.detail(actor(auth),classroom,id));}
    @PostMapping("/{id}/run") @Operation(summary="Active Student runs only sample testcases")
    public ApiResponse<UUID> run(Authentication auth,@PathVariable UUID classroom,@PathVariable UUID id,@Valid @RequestBody ProblemService.CodeInput input){return ApiResponse.success(service.evaluate(actor(auth),classroom,id,input,true));}
    @PostMapping("/{id}/submissions") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Active Student submits practice without an official attempt limit")
    public ApiResponse<UUID> submit(Authentication auth,@PathVariable UUID classroom,@PathVariable UUID id,@Valid @RequestBody ProblemService.CodeInput input){return ApiResponse.success(service.evaluate(actor(auth),classroom,id,input,false));}
    @GetMapping("/{id}/submissions") @Operation(summary="Read only caller's practice submission history")
    public PageResponse<ProblemService.Submission> history(Authentication auth,@PathVariable UUID classroom,@PathVariable UUID id,@PageableDefault(size=20) Pageable page){return service.history(actor(auth),classroom,id,page);}
    @GetMapping("/{id}/jobs/{job}") @Operation(summary="Read only caller's scoped practice judge result")
    public ApiResponse<EvaluationResult> result(Authentication auth,@PathVariable UUID classroom,@PathVariable UUID id,@PathVariable UUID job){return ApiResponse.success(service.result(actor(auth),classroom,id,job));}
    private static UUID actor(Authentication auth){return UUID.fromString(auth.getName());}
}
