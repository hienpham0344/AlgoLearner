package com.algolearner.platform.course;

import com.algolearner.platform.common.dto.ApiResponse;
import com.algolearner.platform.common.dto.PageResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "Public courses (SRS 4.11)", description = "Catalog, preview, enrollment, lessons, quizzes, own progress and Teacher contributions")
@SecurityRequirement(name = "bearerAuth")
public class PublicCourseController {
    private final PublicCourseService learning;
    private final PublicCourseQuizService quizzes;
    private final CourseContributionService contributions;

    // ---------------------------------------------------------------- catalog & learning
    @GetMapping
    @Operation(summary = "Browse and search published courses", description = "Student/Teacher. Filters: q (course name, case-insensitive), level, topic. Max page size 100.")
    public PageResponse<CourseDtos.CatalogItem> catalog(Authentication authentication, @RequestParam(required = false) String q,
            @RequestParam(required = false) CourseDtos.Level level, @RequestParam(required = false) String topic, Pageable pageable) {
        return learning.catalog(actor(authentication), q, level, topic, pageable);
    }

    @GetMapping("/enrollments/me")
    @Operation(summary = "List own enrollments and learning history", description = "Only the authenticated learner's enrollments.")
    public PageResponse<CourseDtos.EnrollmentHistory> myEnrollments(Authentication authentication, Pageable pageable) {
        return learning.myEnrollments(actor(authentication), pageable);
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Course preview and outline", description = "Published courses are visible without enrollment; enrolled learners keep access per unpublish policy; authors see their own course.")
    public ApiResponse<CourseDtos.Detail> detail(Authentication authentication, @PathVariable UUID courseId) {
        return ApiResponse.success(learning.detail(actor(authentication), courseId));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}")
    @Operation(summary = "Read lesson content", description = "Any lesson for enrolled learners (no forced order); preview lessons without enrollment.")
    public ApiResponse<CourseDtos.Lesson> lesson(Authentication authentication, @PathVariable UUID courseId, @PathVariable UUID lessonId) {
        return ApiResponse.success(learning.lesson(actor(authentication), courseId, lessonId));
    }

    @PostMapping("/{courseId}/enrollments")
    @Operation(summary = "Enroll in a published course", description = "Idempotent. Unpublished or archived courses reject new enrollment.")
    public ApiResponse<CourseDtos.Progress> enroll(Authentication authentication, @PathVariable UUID courseId) {
        return ApiResponse.success(learning.enroll(actor(authentication), courseId));
    }

    @DeleteMapping("/{courseId}/enrollments/me")
    @Operation(summary = "Unenroll", description = "Allowed only before any lesson or quiz is started; returns 409 COURSE_ALREADY_STARTED otherwise.")
    public ApiResponse<Void> unenroll(Authentication authentication, @PathVariable UUID courseId) {
        learning.unenroll(actor(authentication), courseId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{courseId}/lessons/{lessonId}/completion")
    @Operation(summary = "Complete a lesson", description = "Requires own enrollment; idempotent.")
    public ApiResponse<CourseDtos.Progress> complete(Authentication authentication, @PathVariable UUID courseId, @PathVariable UUID lessonId) {
        return ApiResponse.success(learning.completeLesson(actor(authentication), courseId, lessonId));
    }

    @GetMapping("/{courseId}/progress")
    @Operation(summary = "Read own course progress", description = "Never accepts another account id.")
    public ApiResponse<CourseDtos.Progress> progress(Authentication authentication, @PathVariable UUID courseId) {
        return ApiResponse.success(learning.progress(actor(authentication), courseId));
    }

    @PostMapping("/{courseId}/progress/update-acknowledgement")
    @Operation(summary = "Dismiss the 'new content' marker", description = "Clears hasUpdate; Completed status is unaffected.")
    public ApiResponse<CourseDtos.Progress> acknowledge(Authentication authentication, @PathVariable UUID courseId) {
        return ApiResponse.success(learning.acknowledgeUpdate(actor(authentication), courseId));
    }

    // ---------------------------------------------------------------- quizzes
    @GetMapping("/{courseId}/quizzes/{quizId}")
    @Operation(summary = "Quiz status and own attempt history", description = "Official score is the highest submitted attempt.")
    public ApiResponse<CourseDtos.QuizStatus> quiz(Authentication authentication, @PathVariable UUID courseId, @PathVariable UUID quizId) {
        return ApiResponse.success(quizzes.status(actor(authentication), courseId, quizId));
    }

    @PostMapping("/{courseId}/quizzes/{quizId}/attempts")
    @Operation(summary = "Start or resume an attempt", description = "Returns the open attempt if any (timer is not reset); 409 QUIZ_ATTEMPTS_EXHAUSTED when no attempts remain.")
    public ApiResponse<CourseDtos.Attempt> start(Authentication authentication, @PathVariable UUID courseId, @PathVariable UUID quizId) {
        return ApiResponse.success(quizzes.start(actor(authentication), courseId, quizId));
    }

    @PutMapping("/{courseId}/quizzes/{quizId}/attempts/{attemptId}/answers")
    @Operation(summary = "Save answers", description = "Merges per-question answers; rejected with 409 after the server-side deadline.")
    public ApiResponse<CourseDtos.Attempt> save(Authentication authentication, @PathVariable UUID courseId, @PathVariable UUID quizId,
            @PathVariable UUID attemptId, @Valid @RequestBody CourseDtos.SaveAnswers request) {
        return ApiResponse.success(quizzes.saveAnswers(actor(authentication), courseId, quizId, attemptId, request));
    }

    @PostMapping("/{courseId}/quizzes/{quizId}/attempts/{attemptId}/submission")
    @Operation(summary = "Submit an attempt", description = "Optional final answers. Auto-graded; answers/explanations revealed per quiz visibility.")
    public ApiResponse<CourseDtos.Attempt> submit(Authentication authentication, @PathVariable UUID courseId, @PathVariable UUID quizId,
            @PathVariable UUID attemptId, @Valid @RequestBody(required = false) CourseDtos.SaveAnswers request) {
        return ApiResponse.success(quizzes.submit(actor(authentication), courseId, quizId, attemptId, request));
    }

    // ---------------------------------------------------------------- teacher contributions
    @GetMapping("/contributions")
    @Operation(summary = "List own contributions", description = "Teacher only; filtered by review status.")
    public PageResponse<CourseDtos.ContributionSummary> contributions(Authentication authentication,
            @RequestParam(defaultValue = "PENDING_REVIEW") ReviewStatus status, Pageable pageable) {
        return contributions.mine(actor(authentication), status, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft course contribution", description = "Teacher only. Not visible to learners until an Admin approves and publishes it.")
    public ApiResponse<CourseDtos.Summary> create(Authentication authentication, @Valid @RequestBody CourseDtos.Create request) {
        return ApiResponse.success(contributions.create(actor(authentication), request));
    }

    @GetMapping("/{courseId}/contribution")
    @Operation(summary = "Read own editable or latest revision", description = "Author only; includes the review reason.")
    public ApiResponse<CourseDtos.Contribution> contribution(Authentication authentication, @PathVariable UUID courseId) {
        return ApiResponse.success(contributions.contribution(actor(authentication), courseId));
    }

    @PutMapping("/{courseId}/contribution")
    @Operation(summary = "Replace revision content", description = "Author only. Editing a published course creates a new revision; the public version is untouched until approved.")
    public ApiResponse<CourseDtos.Contribution> saveContribution(Authentication authentication, @PathVariable UUID courseId,
            @Valid @RequestBody CourseDtos.CourseContent request) {
        return ApiResponse.success(contributions.saveContent(actor(authentication), courseId, request));
    }

    @PostMapping("/{courseId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Append a lesson to the revision", description = "Author only; frozen while pending review.")
    public ApiResponse<CourseDtos.Lesson> addLesson(Authentication authentication, @PathVariable UUID courseId,
            @Valid @RequestBody CourseDtos.UnitRequest request) {
        return ApiResponse.success(contributions.addLesson(actor(authentication), courseId, request));
    }

    @PostMapping("/{courseId}/review-requests")
    @Operation(summary = "Submit revision for review", description = "Author only; requires at least one lesson. Audited.")
    public ApiResponse<CourseDtos.ContributionSummary> submitReview(Authentication authentication, @PathVariable UUID courseId) {
        return ApiResponse.success(contributions.submit(actor(authentication), courseId));
    }

    private UUID actor(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
