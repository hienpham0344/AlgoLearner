package com.algolearner.platform.lesson;

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
@RequestMapping("/api/v1/classrooms/{classId}/lessons")
@RequiredArgsConstructor
@Tag(name = "Classroom Lessons", description = "Classroom Lessons API (Module 4.5)")
@SecurityRequirement(name = "bearerAuth")
public class LessonController {

    private final LessonService service;

    @GetMapping
    @Operation(summary = "List lessons in a classroom")
    public ApiResponse<List<LessonDtos.LessonSummaryView>> list(
            Authentication auth,
            @PathVariable UUID classId
    ) {
        return ApiResponse.success(service.list(actor(auth), classId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a lesson (Teacher owner only)")
    public ApiResponse<LessonDtos.LessonDetailView> create(
            Authentication auth,
            @PathVariable UUID classId,
            @Valid @RequestBody LessonDtos.CreateLessonRequest request
    ) {
        return ApiResponse.success(service.create(actor(auth), classId, request));
    }

    @GetMapping("/{lessonId}")
    @Operation(summary = "Get lesson details")
    public ApiResponse<LessonDtos.LessonDetailView> get(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success(service.get(actor(auth), classId, lessonId));
    }

    @PutMapping("/{lessonId}")
    @Operation(summary = "Update a lesson (Teacher owner only)")
    public ApiResponse<LessonDtos.LessonDetailView> update(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonDtos.UpdateLessonRequest request
    ) {
        return ApiResponse.success(service.update(actor(auth), classId, lessonId, request));
    }

    @DeleteMapping("/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete a lesson if no progress/submissions exist (Teacher owner only)")
    public void delete(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        service.delete(actor(auth), classId, lessonId);
    }

    @PatchMapping("/{lessonId}/publish")
    @Operation(summary = "Publish a lesson (Teacher owner only)")
    public ApiResponse<LessonDtos.LessonDetailView> publish(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success(service.publish(actor(auth), classId, lessonId));
    }

    @PatchMapping("/{lessonId}/unpublish")
    @Operation(summary = "Unpublish a lesson back to draft (Teacher owner only)")
    public ApiResponse<LessonDtos.LessonDetailView> unpublish(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success(service.unpublish(actor(auth), classId, lessonId));
    }

    @PatchMapping("/{lessonId}/archive")
    @Operation(summary = "Archive a lesson (Teacher owner only)")
    public ApiResponse<LessonDtos.LessonDetailView> archive(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success(service.archive(actor(auth), classId, lessonId));
    }

    @PatchMapping("/{lessonId}/restore")
    @Operation(summary = "Restore an archived lesson (Teacher owner only)")
    public ApiResponse<LessonDtos.LessonDetailView> restore(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        return ApiResponse.success(service.restore(actor(auth), classId, lessonId));
    }

    @PostMapping("/{lessonId}/materials")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an attachment material to a lesson (Teacher owner only)")
    public ApiResponse<LessonDtos.MaterialView> addMaterial(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonDtos.AddMaterialRequest request
    ) {
        return ApiResponse.success(service.addMaterial(actor(auth), classId, lessonId, request));
    }

    @PostMapping("/{lessonId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Link an Assignment, Quiz, or Practice to a lesson (Teacher owner only)")
    public ApiResponse<LessonDtos.LinkedItemView> linkItem(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonDtos.LinkItemRequest request
    ) {
        return ApiResponse.success(service.linkItem(actor(auth), classId, lessonId, request));
    }

    @DeleteMapping("/{lessonId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unlink an item from a lesson (Teacher owner only)")
    public void unlinkItem(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId,
            @PathVariable UUID itemId
    ) {
        service.unlinkItem(actor(auth), classId, lessonId, itemId);
    }

    @PostMapping("/{lessonId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Student marks a lesson as completed (Manual mode)")
    public void complete(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID lessonId
    ) {
        service.completeLesson(actor(auth), classId, lessonId);
    }

    private UUID actor(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
