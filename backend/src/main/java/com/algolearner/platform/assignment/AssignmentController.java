package com.algolearner.platform.assignment;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/classrooms/{classId}/assignments")
@RequiredArgsConstructor
@Tag(name = "Classroom Assignments", description = "Classroom Assignments API (Module 4.6)")
@SecurityRequirement(name = "bearerAuth")
public class AssignmentController {

    private final AssignmentService service;

    @GetMapping
    @Operation(summary = "List assignments in a classroom")
    public ApiResponse<List<AssignmentDtos.AssignmentSummaryView>> list(
            Authentication auth,
            @PathVariable UUID classId
    ) {
        return ApiResponse.success(service.list(actor(auth), classId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an assignment (Teacher owner only)")
    public ApiResponse<AssignmentDtos.AssignmentDetailView> create(
            Authentication auth,
            @PathVariable UUID classId,
            @Valid @RequestBody AssignmentDtos.CreateAssignmentRequest request
    ) {
        return ApiResponse.success(service.create(actor(auth), classId, request));
    }

    @GetMapping("/{assignmentId}")
    @Operation(summary = "Get assignment details")
    public ApiResponse<AssignmentDtos.AssignmentDetailView> get(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.success(service.get(actor(auth), classId, assignmentId));
    }

    @PutMapping("/{assignmentId}")
    @Operation(summary = "Update an assignment (Teacher owner only)")
    public ApiResponse<AssignmentDtos.AssignmentDetailView> update(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody AssignmentDtos.UpdateAssignmentRequest request
    ) {
        return ApiResponse.success(service.update(actor(auth), classId, assignmentId, request));
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an assignment if no student submissions exist (Teacher owner only)")
    public void delete(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId
    ) {
        service.delete(actor(auth), classId, assignmentId);
    }

    @PatchMapping("/{assignmentId}/publish")
    @Operation(summary = "Publish an assignment (Teacher owner only)")
    public ApiResponse<AssignmentDtos.AssignmentDetailView> publish(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.success(service.publish(actor(auth), classId, assignmentId));
    }

    @PatchMapping("/{assignmentId}/close")
    @Operation(summary = "Close an assignment to disallow further submissions (Teacher owner only)")
    public ApiResponse<AssignmentDtos.AssignmentDetailView> close(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.success(service.close(actor(auth), classId, assignmentId));
    }

    @PostMapping("/{assignmentId}/publish-grades")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Publish assignment grades for students (Teacher owner only)")
    public void publishGrades(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId
    ) {
        service.publishGrades(actor(auth), classId, assignmentId);
    }

    @PostMapping("/{assignmentId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Clone an assignment into another classroom (Teacher owner only)")
    public ApiResponse<AssignmentDtos.AssignmentDetailView> cloneAssignment(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @RequestParam UUID targetClassroomId
    ) {
        return ApiResponse.success(service.cloneAssignment(actor(auth), classId, assignmentId, targetClassroomId));
    }

    @PostMapping("/{assignmentId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a question to an assignment (Teacher owner only)")
    public ApiResponse<AssignmentDtos.QuestionView> addQuestion(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody AssignmentDtos.AddQuestionRequest request
    ) {
        return ApiResponse.success(service.addQuestion(actor(auth), classId, assignmentId, request));
    }

    @DeleteMapping("/{assignmentId}/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a question from an assignment (Teacher owner only)")
    public void deleteQuestion(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @PathVariable UUID questionId
    ) {
        service.deleteQuestion(actor(auth), classId, assignmentId, questionId);
    }

    @PostMapping("/{assignmentId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start or resume an attempt (Active student only)")
    public ApiResponse<AssignmentDtos.AttemptView> startAttempt(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.success(service.startAttempt(actor(auth), classId, assignmentId));
    }

    @GetMapping("/{assignmentId}/attempts/active")
    @Operation(summary = "Get currently active attempt and draft answers (Active student only)")
    public ApiResponse<AssignmentDtos.AttemptView> getActiveAttempt(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.success(service.getActiveAttempt(actor(auth), classId, assignmentId));
    }

    @PutMapping("/{assignmentId}/attempts/{attemptId}/draft")
    @Operation(summary = "Autosave draft answer for a specific question (Active student only)")
    public ApiResponse<AssignmentDtos.AnswerView> saveDraftAnswer(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @PathVariable UUID attemptId,
            @Valid @RequestBody AssignmentDtos.SaveDraftAnswerRequest request
    ) {
        return ApiResponse.success(service.saveDraftAnswer(actor(auth), classId, assignmentId, attemptId, request));
    }

    @PostMapping("/{assignmentId}/attempts/{attemptId}/submit")
    @Operation(summary = "Submit the official attempt for grading (Active student only)")
    public ApiResponse<AssignmentDtos.AttemptView> submitAttempt(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @PathVariable UUID attemptId
    ) {
        return ApiResponse.success(service.submitAttempt(actor(auth), classId, assignmentId, attemptId));
    }

    @GetMapping("/{assignmentId}/submissions")
    @Operation(summary = "Get all submissions for an assignment (Teacher owner only)")
    public ApiResponse<List<AssignmentDtos.AttemptView>> getSubmissions(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId
    ) {
        return ApiResponse.success(service.getSubmissions(actor(auth), classId, assignmentId));
    }

    @PostMapping("/{assignmentId}/submissions/{attemptId}/grade-essay")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Grade an essay question in a student submission (Teacher owner only)")
    public void gradeEssay(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @PathVariable UUID attemptId,
            @Valid @RequestBody AssignmentDtos.GradeEssayRequest request
    ) {
        service.gradeEssay(actor(auth), classId, assignmentId, attemptId, request);
    }

    @PostMapping("/{assignmentId}/submissions/{attemptId}/override-grade")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Override student final score with an audit reason (Teacher owner only)")
    public void overrideGrade(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @PathVariable UUID attemptId,
            @Valid @RequestBody AssignmentDtos.OverrideGradeRequest request
    ) {
        service.overrideGrade(actor(auth), classId, assignmentId, attemptId, request);
    }

    @PostMapping("/{assignmentId}/rejudge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Rejudge assignment submissions and notify students (Teacher owner only)")
    public void rejudge(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @RequestBody(required = false) AssignmentDtos.RejudgeRequest request
    ) {
        service.rejudge(actor(auth), classId, assignmentId, request);
    }

    private UUID actor(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
