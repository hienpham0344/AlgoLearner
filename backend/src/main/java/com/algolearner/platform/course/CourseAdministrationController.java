package com.algolearner.platform.course;

import com.algolearner.platform.common.dto.ApiResponse;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.course.CourseDtos.Publication;
import com.algolearner.platform.course.CourseDtos.ReviewStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/admin/courses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Public course administration (SRS 4.11)", description = "Review, publication lifecycle and direct content management")
@SecurityRequirement(name = "bearerAuth")
public class CourseAdministrationController {
    private final CourseAdministrationService courses;

    @GetMapping
    @Operation(summary = "List all public courses", description = "Admin only; optional publication status and name filter.")
    public PageResponse<CourseDtos.Summary> list(Authentication authentication, @RequestParam(required = false) Publication status,
            @RequestParam(required = false) String q, Pageable pageable) {
        return courses.list(actor(authentication), status, q, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a course as Admin", description = "Content is stored directly as the public version, in DRAFT publication status.")
    public ApiResponse<CourseDtos.AdminDetail> create(Authentication authentication, @Valid @RequestBody CourseDtos.CourseContent request) {
        return ApiResponse.success(courses.create(actor(authentication), request));
    }

    @GetMapping("/reviews")
    @Operation(summary = "Contribution review queue", description = "Paginated revisions by review status, default PENDING_REVIEW.")
    public PageResponse<CourseDtos.ContributionSummary> queue(Authentication authentication,
            @RequestParam(defaultValue = "PENDING_REVIEW") ReviewStatus status, Pageable pageable) {
        return courses.queue(actor(authentication), status, pageable);
    }

    @GetMapping("/reviews/{revisionId}")
    @Operation(summary = "Read a contribution revision", description = "Full proposed content including correct answers.")
    public ApiResponse<CourseDtos.Contribution> revision(Authentication authentication, @PathVariable UUID revisionId) {
        return ApiResponse.success(courses.revision(actor(authentication), revisionId));
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Read course for management", description = "Public content, open contribution and enrollment count.")
    public ApiResponse<CourseDtos.AdminDetail> detail(Authentication authentication, @PathVariable UUID courseId) {
        return ApiResponse.success(courses.detail(actor(authentication), courseId));
    }

    @PutMapping("/{courseId}/content")
    @Operation(summary = "Replace public content", description = "Admin edit applied immediately; progress of non-completed learners is recalculated, Completed is preserved.")
    public ApiResponse<CourseDtos.AdminDetail> updateContent(Authentication authentication, @PathVariable UUID courseId,
            @Valid @RequestBody CourseDtos.CourseContent request) {
        return ApiResponse.success(courses.updateContent(actor(authentication), courseId, request));
    }

    @PostMapping("/{courseId}/reviews")
    @Operation(summary = "Review the pending contribution", description = "decision APPROVE | CHANGES_REQUESTED | REJECT (legacy: approved=true/false). Reason required unless approving. Approve applies content and, by default for a draft course, publishes it.")
    public ApiResponse<CourseDtos.ContributionSummary> review(Authentication authentication, @PathVariable UUID courseId,
            @Valid @RequestBody CourseDtos.Review request) {
        return ApiResponse.success(courses.review(actor(authentication), courseId, request));
    }

    @PostMapping("/{courseId}/publication")
    @Operation(summary = "Publish, unpublish or archive", description = "UNPUBLISH requires unpublishPolicy ENROLLED_CONTINUE or LOCK_ALL. Enrollment and history are never removed.")
    public ApiResponse<CourseDtos.Summary> publication(Authentication authentication, @PathVariable UUID courseId,
            @Valid @RequestBody CourseDtos.PublicationChange request) {
        return ApiResponse.success(courses.changePublication(actor(authentication), courseId, request));
    }

    @DeleteMapping("/{courseId}")
    @Operation(summary = "Hard delete a course", description = "Only when no enrollment exists; otherwise 409 COURSE_HAS_LEARNING_DATA (archive instead).")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable UUID courseId) {
        courses.delete(actor(authentication), courseId);
        return ApiResponse.success(null);
    }

    private UUID actor(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
