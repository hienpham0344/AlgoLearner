package com.algolearner.platform.grading;

import com.algolearner.platform.common.dto.ApiErrorResponse;
import com.algolearner.platform.common.dto.ApiResponse;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.audit.AuditService.RequestContext;
import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import com.algolearner.platform.grading.GradebookDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mock")
@RequiredArgsConstructor
@RequestMapping("/api/v1/classrooms/{classId}/gradebook")
@Tag(name = "Classroom Gradebook")
@SecurityRequirement(name = "bearerAuth")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Classroom access denied",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Classroom or gradebook not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@PreAuthorize("hasRole('TEACHER')")
public class GradebookController {
    private final GradebookQueryService queries;
    private final GradebookRefreshService refresh;
    private final GradebookSchemeService schemes;
    private final GradebookVisibilityService visibility;
    private final GradebookPublicationService publications;
    private final GradebookHistoryService history;
    private final GradebookExportService exports;

    @GetMapping({"", "/"})
    @Operation(summary = "List the classroom's gradebook rows")
    public PageResponse<TeacherGradebookRow> roster(Authentication principal, @PathVariable UUID classId,
                                                    @PageableDefault(size = 20) Pageable page) {
        return queries.roster(principal, classId, page);
    }

    @GetMapping("/summary")
    @Operation(summary = "Read classroom gradebook statistics")
    public ApiResponse<ClassGradebookSummary> summary(Authentication principal, @PathVariable UUID classId) {
        return ApiResponse.success(queries.summary(principal, classId));
    }

    @GetMapping("/students/{studentId}")
    @Operation(summary = "Read a classroom student's grades")
    public ApiResponse<TeacherGradebookRow> student(Authentication principal, @PathVariable UUID classId,
                                                    @PathVariable UUID studentId) {
        return ApiResponse.success(queries.student(principal, classId, studentId));
    }

    @GetMapping("/items/{itemId}/submissions")
    @Operation(summary = "List submitted attempts and answers for a classroom item")
    public PageResponse<TeacherSubmissionDetail> submissions(Authentication principal, @PathVariable UUID classId,
            @PathVariable UUID itemId, @RequestParam SourceType sourceType, @PageableDefault(size = 20) Pageable page) {
        return queries.submissions(principal, classId, sourceType, itemId, page);
    }

    @GetMapping("/scheme")
    @Operation(summary = "Read the active grade scheme and gradebook version")
    public ApiResponse<SchemeView> scheme(Authentication principal, @PathVariable UUID classId) {
        return ApiResponse.success(schemes.get(principal, classId));
    }

    @PostMapping("/scheme-preview")
    @Operation(summary = "Preview equal item weights")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Hidden item",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApiResponse<List<SchemeItem>> preview(Authentication principal, @PathVariable UUID classId,
            @Valid @RequestBody SchemePreviewRequest request) {
        return ApiResponse.success(schemes.preview(principal, classId, request));
    }

    @PutMapping("/scheme")
    @Operation(summary = "Apply a new immutable grade scheme revision")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Gradebook version conflict",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid weights or hidden/ungraded item",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApiResponse<SchemeView> apply(Authentication principal, @PathVariable UUID classId,
            @Valid @RequestBody SchemeApplyRequest request, HttpServletRequest http) {
        return ApiResponse.success(schemes.apply(principal, classId, request,
                new RequestContext(http.getRemoteAddr(), http.getHeader("User-Agent"))));
    }

    @PutMapping("/visibility")
    @Operation(summary = "Set whether students may see published overall grades")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Gradebook version conflict",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ApiResponse<VisibilityView> visibility(Authentication principal, @PathVariable UUID classId,
            @Valid @RequestBody VisibilityRequest request) {
        return ApiResponse.success(visibility.update(principal, classId, request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Read the current student's published classroom grades")
    public ApiResponse<MyGradebookResponse> me(Authentication principal, @PathVariable UUID classId) {
        var result = queries.me(principal, classId);
        if (result.staleSnapshot() != null) refresh.requestSnapshotRefresh(result.staleSnapshot());
        return ApiResponse.success(result.response());
    }

    @PostMapping("/publications")
    @Operation(summary = "Publish eligible gradebook results to one student or the active class roster")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Publication created", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Existing publication replayed", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Version or idempotency conflict",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "No eligible grade to publish",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<ApiResponse<GradePublicationResponse>> publish(Authentication principal, @PathVariable UUID classId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody GradePublicationRequest request,
            HttpServletRequest http) {
        var result = publications.publish(principal, classId, key, request,
                new RequestContext(http.getRemoteAddr(), http.getHeader("User-Agent")));
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(result.response()));
    }

    @GetMapping("/history")
    @Operation(summary = "List the classroom's gradebook change history")
    public PageResponse<GradebookHistoryEntry> history(Authentication principal, @PathVariable UUID classId,
            @PageableDefault(size = 20) Pageable page) {
        return history.list(principal, classId, page);
    }

    @GetMapping("/export")
    @Operation(summary = "Download the classroom gradebook as CSV or XLSX")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gradebook attachment",
            content = {@Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary"))})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Export exceeds the configured limits",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public void export(Authentication principal, @PathVariable UUID classId, @RequestParam String format,
                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (var download = exports.export(principal, classId, format,
                new RequestContext(request.getRemoteAddr(), request.getHeader("User-Agent")))) {
            response.setContentType(download.mediaType().toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(download.filename(classId)).build().toString());
            if (download.schemeRevision() != null) response.setHeader("X-Gradebook-Scheme-Revision", download.schemeRevision().toString());
            response.setContentLengthLong(Files.size(download.path()));
            Files.copy(download.path(), response.getOutputStream());
        }
    }
}
