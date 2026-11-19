package com.algolearner.platform.quiz;

import com.algolearner.platform.common.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequiredArgsConstructor
@RequestMapping("/api/v1/classrooms/{classId}/quizzes")
@Tag(name="Classroom Quizzes") @SecurityRequirement(name="bearerAuth")
public class QuizController {
    private final QuizService quizzes;
    @GetMapping @Operation(summary="List quizzes visible to the current classroom member")
    public ApiResponse<List<QuizDtos.QuizView>> list(Authentication auth,@PathVariable UUID classId){return ApiResponse.success(quizzes.list(actor(auth),classId));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Create an objective quiz draft (Teacher owner)")
    public ApiResponse<QuizDtos.QuizView> create(Authentication auth,@PathVariable UUID classId,@Valid @RequestBody QuizDtos.Configure request){return ApiResponse.success(quizzes.create(actor(auth),classId,request));}
    @GetMapping("/{quizId}") @Operation(summary="Read quiz availability without answer keys")
    public ApiResponse<QuizDtos.QuizView> get(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId){return ApiResponse.success(quizzes.get(actor(auth),classId,quizId));}
    @GetMapping("/{quizId}/authoring") @Operation(summary="Read authored questions and answer keys (Teacher owner only)")
    public ApiResponse<QuizDtos.Configure> authoring(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId){return ApiResponse.success(quizzes.authoring(actor(auth),classId,quizId));}
    @PutMapping("/{quizId}") @Operation(summary="Update a quiz draft (Teacher owner)")
    public ApiResponse<QuizDtos.QuizView> update(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId,@Valid @RequestBody QuizDtos.Configure request){return ApiResponse.success(quizzes.update(actor(auth),classId,quizId,request));}
    @PostMapping("/{quizId}/publish") @Operation(summary="Publish a validated quiz draft")
    public ApiResponse<QuizDtos.QuizView> publish(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId){return ApiResponse.success(quizzes.publish(actor(auth),classId,quizId));}
    @PostMapping("/{quizId}/close") @Operation(summary="Close a quiz to new attempts while preserving history")
    public ApiResponse<QuizDtos.QuizView> close(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId){return ApiResponse.success(quizzes.close(actor(auth),classId,quizId));}
    @PostMapping("/{quizId}/attempts") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Start or resume a frozen quiz attempt (Student)")
    public ApiResponse<QuizDtos.AttemptView> start(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId){return ApiResponse.success(quizzes.start(actor(auth),classId,quizId));}
    @GetMapping("/{quizId}/attempts") @Operation(summary="Page own attempt history, or classroom history for Teacher owner")
    public PageResponse<QuizDtos.AttemptView> history(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId,@RequestParam(required=false) UUID studentId,Pageable page){return quizzes.history(actor(auth),classId,quizId,studentId,page);}
    @GetMapping("/{quizId}/attempts/{attemptId}") @Operation(summary="Resume saved answers and the original server cutoff")
    public ApiResponse<QuizDtos.AttemptView> resume(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId,@PathVariable UUID attemptId){return ApiResponse.success(quizzes.resume(actor(auth),classId,quizId,attemptId));}
    @PutMapping("/{quizId}/attempts/{attemptId}/answers") @Operation(summary="Autosave one typed answer with per-question version and idempotency key")
    public ApiResponse<QuizDtos.SaveReceipt> save(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId,@PathVariable UUID attemptId,@Valid @RequestBody QuizDtos.SaveAnswer request){return ApiResponse.success(quizzes.save(actor(auth),classId,quizId,attemptId,request));}
    @PostMapping("/{quizId}/attempts/{attemptId}/submit") @Operation(summary="Idempotently submit only the saved answers")
    public ApiResponse<QuizDtos.AttemptView> submit(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId,@PathVariable UUID attemptId){return ApiResponse.success(quizzes.submit(actor(auth),classId,quizId,attemptId));}
    @PostMapping("/{quizId}/publish-grades") @Operation(summary="Publish official grades without exposing answer keys (Teacher owner)")
    public ApiResponse<Void> grades(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId){quizzes.publishGrades(actor(auth),classId,quizId);return ApiResponse.success(null);}
    @PostMapping("/{quizId}/attempt-rights") @Operation(summary="Grant or reset attempt allowance with a reason; preserve locked drafts and official history (Teacher owner)")
    public ApiResponse<Void> adjust(Authentication auth,@PathVariable UUID classId,@PathVariable UUID quizId,@Valid @RequestBody QuizDtos.AdjustAttempts request){
        quizzes.adjustAttempts(actor(auth),classId,quizId,request.studentId(),request.action(),request.additionalAttempts(),request.reason());return ApiResponse.success(null);
    }
    private UUID actor(Authentication auth){return UUID.fromString(auth.getName());}
}
