package com.algolearner.platform.classroom;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.algolearner.platform.classroom.ClassroomDtos.*;

@RestController
@RequestMapping("/api/v1/classrooms")
@Tag(name = "Classroom Management", description = "SRS 4.3 private classroom lifecycle and membership")
@SecurityRequirement(name = "bearerAuth")
public class ClassroomController {
    private final ClassroomService service;
    public ClassroomController(ClassroomService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a private classroom")
    public ApiResponse<View> create(Authentication auth, @Valid @RequestBody Create input) { return ApiResponse.success(service.create(actor(auth), input)); }
    @GetMapping
    public ApiResponse<java.util.List<View>> list(Authentication auth) { return ApiResponse.success(service.list(actor(auth))); }
    @GetMapping("/{classroomId}")
    public ApiResponse<View> get(Authentication auth, @PathVariable UUID classroomId) { return ApiResponse.success(service.get(actor(auth), classroomId)); }
    @PatchMapping("/{classroomId}")
    public ApiResponse<View> update(Authentication auth, @PathVariable UUID classroomId, @Valid @RequestBody Update input) { return ApiResponse.success(service.update(actor(auth), classroomId, input)); }
    @PostMapping("/{classroomId}/archive") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(Authentication auth, @PathVariable UUID classroomId) { service.archive(actor(auth), classroomId); }
    @DeleteMapping("/{classroomId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID classroomId) { service.delete(actor(auth), classroomId); }
    @PatchMapping("/{classroomId}/join-configuration")
    public ApiResponse<View> configure(Authentication auth, @PathVariable UUID classroomId, @Valid @RequestBody CodeConfig input) { return ApiResponse.success(service.configureCode(actor(auth), classroomId, input)); }
    @PostMapping("/{classroomId}/class-code/regenerate")
    public ApiResponse<View> regenerate(Authentication auth, @PathVariable UUID classroomId) { return ApiResponse.success(service.regenerateCode(actor(auth), classroomId)); }
    @PostMapping("/join")
    public ApiResponse<JoinResult> join(Authentication auth, @Valid @RequestBody JoinByCode input) { return ApiResponse.success(service.join(actor(auth), input)); }
    @PostMapping("/{classroomId}/leave") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(Authentication auth, @PathVariable UUID classroomId) { service.leave(actor(auth), classroomId); }
    @PostMapping("/{classroomId}/members/direct") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void directAdd(Authentication auth, @PathVariable UUID classroomId, @Valid @RequestBody DirectAdd input) { service.directAdd(actor(auth), classroomId, input); }
    @DeleteMapping("/{classroomId}/members/{studentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID studentId) { service.removeStudent(actor(auth), classroomId, studentId); }
    @GetMapping("/{classroomId}/members")
    public ApiResponse<java.util.List<Member>> members(Authentication auth, @PathVariable UUID classroomId) { return ApiResponse.success(service.activeMembers(actor(auth), classroomId)); }
    @GetMapping("/{classroomId}/join-requests")
    public ApiResponse<java.util.List<JoinRequest>> requests(Authentication auth, @PathVariable UUID classroomId) { return ApiResponse.success(service.pendingRequests(actor(auth), classroomId)); }
    @PostMapping("/{classroomId}/join-requests/{requestId}/approve") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID requestId) { service.approve(actor(auth), classroomId, requestId); }
    @PostMapping("/{classroomId}/invitations") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UUID> invite(Authentication auth, @PathVariable UUID classroomId, @Valid @RequestBody Invite input) { return ApiResponse.success(service.invite(actor(auth), classroomId, input)); }
    @PostMapping("/invitations/{invitationId}/accept") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptInvitation(Authentication auth, @PathVariable UUID invitationId) { service.acceptInvitation(actor(auth), invitationId); }
    @PostMapping("/invitations/{invitationId}/decline") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineInvitation(Authentication auth, @PathVariable UUID invitationId) { service.declineInvitation(actor(auth), invitationId); }
    @PostMapping("/{classroomId}/invitations/{invitationId}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID invitationId) { service.revokeInvitation(actor(auth), classroomId, invitationId); }
    @PostMapping("/{classroomId}/transfer-owner") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(Authentication auth, @PathVariable UUID classroomId, @Valid @RequestBody TransferOwner input) { service.transferOwner(actor(auth), classroomId, input.targetTeacherId()); }
    private UUID actor(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
