package com.algolearner.platform.classroom;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.algolearner.platform.classroom.ClassroomDtos.*;

@RestController
@RequestMapping(value="/api/v1/admin/classrooms",produces=MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name="Admin Classrooms")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="Invalid request"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="Authentication required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403",description="Admin role required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="Classroom not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="Operation conflicts with classroom state")
})
public class AdminClassroomController {
    private final ClassroomService service;
    public AdminClassroomController(ClassroomService service) { this.service=service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Create a classroom for an active Teacher")
    public ApiResponse<ClassroomResponse> create(Authentication auth,
            @Valid @RequestBody AdminCreateClassroomRequest request) {
        return ApiResponse.success("Classroom created",service.adminCreate(id(auth),request));
    }

    @PostMapping("/{classroomId}/transfer-ownership")
    @Operation(summary="Transfer ownership to another active Teacher and write an audit event")
    public ApiResponse<ClassroomResponse> transfer(Authentication auth,@PathVariable UUID classroomId,
            @Valid @RequestBody TransferOwnershipRequest request) {
        return ApiResponse.success("Classroom ownership transferred",
                service.transferOwnership(id(auth),classroomId,request));
    }

    private UUID id(Authentication auth) { return UUID.fromString(auth.getName()); }
}
