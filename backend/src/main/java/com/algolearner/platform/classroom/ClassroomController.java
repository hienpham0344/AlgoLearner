package com.algolearner.platform.classroom;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import static com.algolearner.platform.classroom.ClassroomDtos.*;

@RestController
@RequestMapping(value="/api/v1/classrooms",produces=MediaType.APPLICATION_JSON_VALUE)
@Tag(name="Classrooms",description="SRS 4.3 classroom, membership, join request and invitation management")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="Invalid request"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="Authentication required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403",description="Insufficient role or classroom scope"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="Classroom or invitation not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="Operation conflicts with classroom state")
})
public class ClassroomController {
    private final ClassroomService service;
    public ClassroomController(ClassroomService service) { this.service=service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="Create a private classroom owned by the authenticated Teacher")
    public ApiResponse<ClassroomResponse> create(Authentication auth,@Valid @RequestBody CreateClassroomRequest request) {
        return ApiResponse.success("Classroom created",service.create(id(auth),request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    @Operation(summary="List classrooms visible to the authenticated account")
    public ApiResponse<List<ClassroomResponse>> list(Authentication auth) {
        return ApiResponse.success(service.list(id(auth)));
    }

    @GetMapping("/{classroomId}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    @Operation(summary="Read one classroom within the authenticated account's scope")
    public ApiResponse<ClassroomResponse> get(Authentication auth,@PathVariable UUID classroomId) {
        return ApiResponse.success(service.get(id(auth),classroomId));
    }

    @PatchMapping("/{classroomId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary="Update classroom metadata as its owner or an Admin")
    public ApiResponse<ClassroomResponse> update(Authentication auth,@PathVariable UUID classroomId,
            @Valid @RequestBody UpdateClassroomRequest request) {
        return ApiResponse.success(service.update(id(auth),classroomId,request));
    }

    @PostMapping("/{classroomId}/archive")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary="Archive a classroom without removing preserved learning data")
    public ApiResponse<ClassroomResponse> archive(Authentication auth,@PathVariable UUID classroomId) {
        return ApiResponse.success("Classroom archived",service.archive(id(auth),classroomId));
    }

    @DeleteMapping("/{classroomId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary="Permanently delete an empty classroom")
    public ResponseEntity<Void> delete(Authentication auth,@PathVariable UUID classroomId) {
        service.delete(id(auth),classroomId); return ResponseEntity.noContent().build();
    }

    @GetMapping("/{classroomId}/join-code")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary="Read the current class code as the owner or an Admin")
    public ApiResponse<JoinCodeResponse> joinCode(Authentication auth,@PathVariable UUID classroomId) {
        return ApiResponse.success(service.joinCode(id(auth),classroomId));
    }

    @PostMapping("/{classroomId}/join-code/regenerate")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="Regenerate the class code and invalidate the previous code")
    public ApiResponse<JoinCodeResponse> regenerateCode(Authentication auth,@PathVariable UUID classroomId) {
        return ApiResponse.success("Class code regenerated",service.regenerateCode(id(auth),classroomId));
    }

    @PatchMapping("/{classroomId}/join-code")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="Lock, unlock, or change the class code expiry")
    public ApiResponse<JoinCodeResponse> updateJoinCode(Authentication auth,@PathVariable UUID classroomId,
            @Valid @RequestBody JoinCodeSettingsRequest request) {
        return ApiResponse.success(service.updateJoinCode(id(auth),classroomId,request));
    }

    @PatchMapping("/{classroomId}/join-settings")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="Configure open joining and approval mode")
    public ApiResponse<ClassroomResponse> updateJoinSettings(Authentication auth,@PathVariable UUID classroomId,
            @Valid @RequestBody JoinSettingsRequest request) {
        return ApiResponse.success(service.updateJoinSettings(id(auth),classroomId,request));
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary="Join a classroom or create a pending join request using a class code")
    public ApiResponse<JoinResult> join(Authentication auth,@Valid @RequestBody JoinClassRequest request) {
        return ApiResponse.success(service.join(id(auth),request));
    }

    @PostMapping("/{classroomId}/leave")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary="Leave a classroom while preserving learning history")
    public ApiResponse<MembershipResponse> leave(Authentication auth,@PathVariable UUID classroomId) {
        return ApiResponse.success(service.leave(id(auth),classroomId));
    }

    @GetMapping("/{classroomId}/members")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary="List classroom memberships as the owner or an Admin")
    public ApiResponse<List<MembershipResponse>> members(Authentication auth,@PathVariable UUID classroomId) {
        return ApiResponse.success(service.members(id(auth),classroomId));
    }

    @PostMapping("/{classroomId}/members")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Add a registered Student directly to a classroom")
    public ApiResponse<MembershipResponse> addMember(Authentication auth,@PathVariable UUID classroomId,
            @Valid @RequestBody AddMemberRequest request) {
        return ApiResponse.success(service.addMember(id(auth),classroomId,request));
    }

    @DeleteMapping("/{classroomId}/members/{studentAccountId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="Remove a Student while preserving their learning history")
    public ApiResponse<MembershipResponse> removeMember(Authentication auth,@PathVariable UUID classroomId,
            @PathVariable UUID studentAccountId) {
        return ApiResponse.success(service.removeMember(id(auth),classroomId,studentAccountId));
    }

    @GetMapping("/{classroomId}/join-requests")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="List join requests for an owned classroom")
    public ApiResponse<List<JoinRequestResponse>> joinRequests(Authentication auth,@PathVariable UUID classroomId) {
        return ApiResponse.success(service.joinRequests(id(auth),classroomId));
    }

    @PostMapping("/{classroomId}/join-requests/{requestId}/approve")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="Approve a pending classroom join request")
    public ApiResponse<JoinRequestResponse> approve(Authentication auth,@PathVariable UUID classroomId,
            @PathVariable UUID requestId) {
        return ApiResponse.success(service.reviewJoinRequest(id(auth),classroomId,requestId,true));
    }

    @PostMapping("/{classroomId}/join-requests/{requestId}/reject")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="Reject a pending classroom join request")
    public ApiResponse<JoinRequestResponse> reject(Authentication auth,@PathVariable UUID classroomId,
            @PathVariable UUID requestId) {
        return ApiResponse.success(service.reviewJoinRequest(id(auth),classroomId,requestId,false));
    }

    @PostMapping("/{classroomId}/invitations")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Invite a registered Student by email")
    public ApiResponse<InvitationResponse> invite(Authentication auth,@PathVariable UUID classroomId,
            @Valid @RequestBody CreateInvitationRequest request) {
        return ApiResponse.success(service.invite(id(auth),classroomId,request));
    }

    @GetMapping("/{classroomId}/invitations")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="List invitations for an owned classroom")
    public ApiResponse<List<InvitationResponse>> invitations(Authentication auth,@PathVariable UUID classroomId) {
        return ApiResponse.success(service.invitations(id(auth),classroomId));
    }

    @PostMapping("/{classroomId}/invitations/{invitationId}/revoke")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary="Revoke a pending classroom invitation")
    public ApiResponse<InvitationResponse> revokeInvitation(Authentication auth,@PathVariable UUID classroomId,
            @PathVariable UUID invitationId) {
        return ApiResponse.success(service.revokeInvitation(id(auth),classroomId,invitationId));
    }

    @PostMapping("/invitations/{invitationId}/accept")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary="Accept an invitation addressed to the authenticated Student")
    public ApiResponse<InvitationResponse> acceptInvitation(Authentication auth,@PathVariable UUID invitationId) {
        return ApiResponse.success(service.respondInvitation(id(auth),invitationId,true));
    }

    @PostMapping("/invitations/{invitationId}/decline")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary="Decline an invitation addressed to the authenticated Student")
    public ApiResponse<InvitationResponse> declineInvitation(Authentication auth,@PathVariable UUID invitationId) {
        return ApiResponse.success(service.respondInvitation(id(auth),invitationId,false));
    }

    private UUID id(Authentication auth) { return UUID.fromString(auth.getName()); }
}
