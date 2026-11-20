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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/admin/courses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Course contribution review")
@SecurityRequirement(name = "bearerAuth")
public class CourseAdministrationController {
    private final PublicCourseService courses;

    @GetMapping
    @Operation(summary = "List course review queue", description = "Admin only; paginated real contributions, default PENDING_REVIEW.")
    public PageResponse<CourseDtos.Summary> queue(Authentication authentication,
            @RequestParam(defaultValue = "PENDING_REVIEW") String status, Pageable pageable) {
        return courses.list(actor(authentication),status,false,true,pageable);
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Read course for moderation", description = "Admin context only, including draft and pending content.")
    public ApiResponse<CourseDtos.Detail> detail(Authentication authentication,@PathVariable UUID courseId) {
        return ApiResponse.success(courses.administrationDetail(actor(authentication),courseId));
    }

    @PostMapping("/{courseId}/reviews")
    @Operation(summary = "Approve or reject a submitted course", description = "Admin only; rejection requires a reason. Status and audit commit atomically.")
    public ApiResponse<CourseDtos.Summary> review(Authentication authentication,@PathVariable UUID courseId,
            @Valid @RequestBody CourseDtos.Review request) {
        return ApiResponse.success(courses.review(actor(authentication),courseId,request));
    }

    private UUID actor(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
