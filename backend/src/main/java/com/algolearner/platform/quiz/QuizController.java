package com.algolearner.platform.quiz;

import com.algolearner.platform.common.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.algolearner.platform.quiz.QuizDtos.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/classrooms/{classroomId}/quizzes")
@Tag(name="Classroom Quiz", description="SRS 4.7: teacher management, independent autosave and server-controlled attempts")
@SecurityRequirement(name="bearerAuth")
@PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
@ApiResponses({@ApiResponse(responseCode="400",description="Invalid configuration or answer"),
    @ApiResponse(responseCode="401",description="Authentication required"),
    @ApiResponse(responseCode="403",description="Role, ownership or membership denied"),
    @ApiResponse(responseCode="404",description="Resource not found in this classroom"),
    @ApiResponse(responseCode="409",description="Quiz unavailable, attempt limit, expiry or frozen configuration")})
public class QuizController {
    private final QuizService service;
    public QuizController(QuizService service) { this.service=service; }

    @PostMapping("")
    @Operation(summary="Create draft quiz; owner only")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public com.algolearner.platform.common.dto.ApiResponse<QuizView> create(Authentication auth, @PathVariable UUID classroomId, @Valid @RequestBody Write input) { return com.algolearner.platform.common.dto.ApiResponse.success(service.create(actor(auth),classroomId,input)); }

    @GetMapping("")
    @Operation(summary="List visible quizzes with pagination")
    public PageResponse<QuizView> list(Authentication auth, @PathVariable UUID classroomId, @PageableDefault(size=20) Pageable page) { return service.list(actor(auth),classroomId,page); }

    @GetMapping("/{quizId}")
    @Operation(summary="Read quiz; student responses exclude answer keys")
    public com.algolearner.platform.common.dto.ApiResponse<QuizView> get(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId) { return com.algolearner.platform.common.dto.ApiResponse.success(service.get(actor(auth),classroomId,quizId)); }

    @PutMapping("/{quizId}")
    @Operation(summary="Replace configuration before first attempt; title remains editable")
    @PreAuthorize("hasRole('TEACHER')")
    public com.algolearner.platform.common.dto.ApiResponse<QuizView> update(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId, @Valid @RequestBody Write input) { return com.algolearner.platform.common.dto.ApiResponse.success(service.update(actor(auth),classroomId,quizId,input)); }

    @PutMapping("/{quizId}/status")
    @Operation(summary="Set DRAFT, PUBLISHED or CLOSED while preserving attempts")
    @PreAuthorize("hasRole('TEACHER')")
    public com.algolearner.platform.common.dto.ApiResponse<QuizView> status(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId, @Valid @RequestBody ChangeStatus input) { return com.algolearner.platform.common.dto.ApiResponse.success(service.status(actor(auth),classroomId,quizId,input.status())); }

    @PostMapping("/{quizId}/duplicate")
    @Operation(summary="Duplicate configuration into independent draft without student data")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public com.algolearner.platform.common.dto.ApiResponse<QuizView> duplicate(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId) { return com.algolearner.platform.common.dto.ApiResponse.success(service.duplicate(actor(auth),classroomId,quizId)); }

    @PutMapping("/{quizId}/grade-publication")
    @Operation(summary="Publish or withdraw grades in manual mode")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publication(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId, @Valid @RequestBody PublishGrades input) { service.publishGrades(actor(auth),classroomId,quizId,input.published()); }

    @PostMapping("/{quizId}/attempts")
    @Operation(summary="Start or resume attempt with persisted randomized order and server timer")
    @PreAuthorize("hasRole('STUDENT')")
    public com.algolearner.platform.common.dto.ApiResponse<AttemptView> start(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId) { return com.algolearner.platform.common.dto.ApiResponse.success(service.start(actor(auth),classroomId,quizId)); }

    @GetMapping("/{quizId}/attempts")
    @Operation(summary="List own attempts or owner classroom attempts")
    public PageResponse<AttemptView> attempts(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId, @PageableDefault(size=20) Pageable page) { return service.attempts(actor(auth),classroomId,quizId,page); }

    @GetMapping("/{quizId}/attempts/{attemptId}")
    @Operation(summary="Resume saved answers without resetting server deadline")
    public com.algolearner.platform.common.dto.ApiResponse<AttemptView> resume(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId, @PathVariable UUID attemptId) { return com.algolearner.platform.common.dto.ApiResponse.success(service.resume(actor(auth),classroomId,quizId,attemptId)); }

    @PutMapping("/{quizId}/attempts/{attemptId}/answers/{questionId}")
    @Operation(summary="Save one answer atomically; reject answers at or after timer expiry")
    @PreAuthorize("hasRole('STUDENT')")
    public com.algolearner.platform.common.dto.ApiResponse<AttemptView> autosave(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId, @PathVariable UUID attemptId, @PathVariable UUID questionId, @Valid @RequestBody Answer input) { return com.algolearner.platform.common.dto.ApiResponse.success(service.autosave(actor(auth),classroomId,quizId,attemptId,questionId,input)); }

    @PostMapping("/{quizId}/attempts/{attemptId}/submit")
    @Operation(summary="Submit idempotently; grade saved answers only")
    @PreAuthorize("hasRole('STUDENT')")
    public com.algolearner.platform.common.dto.ApiResponse<AttemptView> submit(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId, @PathVariable UUID attemptId) { return com.algolearner.platform.common.dto.ApiResponse.success(service.submit(actor(auth),classroomId,quizId,attemptId)); }

    @GetMapping("/{quizId}/students/{studentId}/result")
    @Operation(summary="Read highest official score, override and completion subject to publication")
    public com.algolearner.platform.common.dto.ApiResponse<Result> result(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId, @PathVariable UUID studentId) { return com.algolearner.platform.common.dto.ApiResponse.success(service.result(actor(auth),classroomId,quizId,studentId)); }

    @PutMapping("/{quizId}/students/{studentId}/access-override")
    @Operation(summary="Reset allowance or set extra attempts and future timer/deadline overrides")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accessOverride(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId, @PathVariable UUID studentId, @Valid @RequestBody AccessOverride input) { service.overrideAccess(actor(auth),classroomId,quizId,studentId,input); }

    @PostMapping("/{quizId}/students/{studentId}/grade-overrides")
    @Operation(summary="Append audited grade override; preserve original automatic score")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public com.algolearner.platform.common.dto.ApiResponse<GradeChange> gradeOverride(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId, @PathVariable UUID studentId, @Valid @RequestBody GradeOverride input) { return com.algolearner.platform.common.dto.ApiResponse.success(service.overrideGrade(actor(auth),classroomId,quizId,studentId,input)); }
    @DeleteMapping("/{quizId}")
    @Operation(summary="Delete unused draft; preserve any quiz with attempts")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID quizId) {
        service.delete(actor(auth),classroomId,quizId);
    }
    @GetMapping("/{quizId}/students/{studentId}/grade-overrides")
    @Operation(summary="Read append-only grade override audit history; owner only")
    @PreAuthorize("hasRole('TEACHER')")
    public PageResponse<GradeChange> gradeHistory(Authentication auth, @PathVariable UUID classroomId,
            @PathVariable UUID quizId, @PathVariable UUID studentId, @PageableDefault(size=20) Pageable page) {
        return service.gradeHistory(actor(auth),classroomId,quizId,studentId,page);
    }
    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }
}
