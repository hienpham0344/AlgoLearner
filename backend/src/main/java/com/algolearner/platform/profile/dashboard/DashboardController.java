package com.algolearner.platform.profile.dashboard;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(produces=MediaType.APPLICATION_JSON_VALUE)
@Tag(name="Dashboards",description="Role-scoped summaries under SRS section 4.2")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="Invalid preview or chart parameters"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="Invalid or missing token"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403",description="Account is not allowed to access this dashboard")
})
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service=service; }

    @GetMapping("/api/v1/dashboards/student")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary="Student dashboard",description="FR-PRO-003: only the authenticated student's learning data")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Student summary")
    public ApiResponse<DashboardResponse.Student> student(Authentication authentication,
            @RequestParam(defaultValue="5") int previewSize,@RequestParam(defaultValue="7") int upcomingDays) {
        return ApiResponse.success(service.student(UUID.fromString(authentication.getName()),previewSize,upcomingDays));
    }

    @GetMapping("/api/v1/dashboards/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="Teacher dashboard",description="FR-PRO-004: only classes currently owned by the authenticated teacher")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Teacher summary")
    public ApiResponse<DashboardResponse.Teacher> teacher(Authentication authentication,
            @RequestParam(defaultValue="5") int previewSize,@RequestParam(defaultValue="7") int upcomingDays) {
        return ApiResponse.success(service.teacher(UUID.fromString(authentication.getName()),previewSize,upcomingDays));
    }

    @GetMapping("/api/v1/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary="Admin dashboard",description="FR-PRO-005. from/to are UTC instants, [from,to); interval=day|week|month. "
            +"Range affects charts only; totals cover all retained source records, including inactive, archived and draft records. "
            +"Default chart range: trailing 30 days. All chart boundaries use UTC+7; weekly buckets start Monday.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Admin summary")
    public ApiResponse<DashboardResponse.Admin> admin(Authentication authentication,
            @RequestParam(defaultValue="5") int previewSize,@RequestParam(required=false) Instant from,
            @RequestParam(required=false) Instant to,@RequestParam(defaultValue="day") String interval) {
        return ApiResponse.success(service.admin(UUID.fromString(authentication.getName()),previewSize,from,to,interval));
    }
}
