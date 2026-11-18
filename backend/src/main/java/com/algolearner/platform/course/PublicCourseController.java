package com.algolearner.platform.course;

import com.algolearner.platform.common.dto.ApiResponse;
import com.algolearner.platform.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "Public course learning", description = "Published lessons, enrollment and own progress required by the dashboard")
@SecurityRequirement(name = "bearerAuth")
public class PublicCourseController {
    private final PublicCourseService courses;

    @GetMapping
    @Operation(summary = "Browse published courses", description = "Student and Teacher access; stable pagination, maximum page size 100.")
    public ApiResponse<PageResponse<CourseDtos.Summary>> list(Authentication authentication, Pageable pageable) {
        return ApiResponse.success(courses.list(actor(authentication),"PUBLISHED",false,false,pageable));
    }

    @GetMapping("/contributions")
    @Operation(summary = "List own course contributions", description = "Teacher only; includes only the current author's contributions in the requested state.")
    public ApiResponse<PageResponse<CourseDtos.Summary>> contributions(Authentication authentication,
            @RequestParam(defaultValue = "PENDING_REVIEW") String status, Pageable pageable) {
        return ApiResponse.success(courses.list(actor(authentication),status,true,false,pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft course", description = "Teacher only. Drafts are not available for enrollment.")
    public ApiResponse<CourseDtos.Summary> create(Authentication authentication,@Valid @RequestBody CourseDtos.Create request) {
        return ApiResponse.success(courses.create(actor(authentication),request));
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Read a course", description = "Published courses are visible to learners; drafts are visible only to their author.")
    public ApiResponse<CourseDtos.Detail> detail(Authentication authentication,@PathVariable UUID courseId) {
        return ApiResponse.success(courses.detail(actor(authentication),courseId));
    }

    @PostMapping("/{courseId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a course lesson", description = "Draft author only. Published and pending-review content is frozen.")
    public ApiResponse<CourseDtos.Unit> addLesson(Authentication authentication,@PathVariable UUID courseId,
            @Valid @RequestBody CourseDtos.UnitRequest request) {
        return ApiResponse.success(courses.addUnit(actor(authentication),courseId,request));
    }

    @PostMapping("/{courseId}/review-requests")
    @Operation(summary = "Submit course for review", description = "Author only; at least one valid lesson is required. Records audit atomically.")
    public ApiResponse<CourseDtos.Summary> submitReview(Authentication authentication,@PathVariable UUID courseId) {
        return ApiResponse.success(courses.submitReview(actor(authentication),courseId));
    }

    @PostMapping("/{courseId}/enrollments")
    @Operation(summary = "Enroll in a published course", description = "Idempotent for the current learner; returns real persisted progress.")
    public ApiResponse<CourseDtos.Progress> enroll(Authentication authentication,@PathVariable UUID courseId) {
        return ApiResponse.success(courses.enroll(actor(authentication),courseId));
    }

    @PostMapping("/{courseId}/lessons/{unitId}/completion")
    @Operation(summary = "Complete a course lesson", description = "Requires own enrollment and a lesson belonging to the requested course; retry does not duplicate progress.")
    public ApiResponse<CourseDtos.Progress> complete(Authentication authentication,@PathVariable UUID courseId,@PathVariable UUID unitId) {
        return ApiResponse.success(courses.complete(actor(authentication),courseId,unitId));
    }

    @GetMapping("/{courseId}/progress")
    @Operation(summary = "Read own course progress", description = "Does not accept another account ID; only the authenticated learner's progress is returned.")
    public ApiResponse<CourseDtos.Progress> progress(Authentication authentication,@PathVariable UUID courseId) {
        return ApiResponse.success(courses.progress(actor(authentication),courseId));
    }

    private UUID actor(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
