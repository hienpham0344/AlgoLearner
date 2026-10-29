package com.algolearner.platform.profile;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import static com.algolearner.platform.profile.ProfileDtos.*;

@RestController
@RequestMapping(value="/api/v1/users",produces=MediaType.APPLICATION_JSON_VALUE)
@Tag(name="User Profiles",description="FR-PRO-001 and FR-PRO-002")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="Invalid or read-only request field"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="Authentication required for owner operations"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403",description="Account or role is not permitted"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="Profile or avatar is not visible or does not exist")
})
public class ProfileController {
    private final ProfileService service;
    public ProfileController(ProfileService service) { this.service=service; }
    @GetMapping("/me/profile")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    @Operation(summary="Read own profile and Public Practice statistics",security=@SecurityRequirement(name="bearerAuth"))
    public ApiResponse<UserProfile> own(Authentication auth) { return ApiResponse.success(service.own(id(auth))); }
    @PatchMapping("/me/profile")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    @Operation(summary="Update own fullName and shortBio only",security=@SecurityRequirement(name="bearerAuth"))
    public ApiResponse<UserProfile> update(Authentication auth,@RequestBody ProfileUpdate request) { return ApiResponse.success(service.update(id(auth),request)); }
    @PatchMapping("/me/profile/privacy")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    @Operation(summary="Set own public profile preference",security=@SecurityRequirement(name="bearerAuth"))
    public ApiResponse<UserProfile> privacy(Authentication auth,@Valid @RequestBody PrivacyUpdate request) { return ApiResponse.success(service.privacy(id(auth),request)); }
    @PutMapping(value="/me/profile/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    @Operation(summary="Validate and replace own avatar",security=@SecurityRequirement(name="bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="413",description="Avatar exceeds configured size"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="415",description="Actual image type is not supported"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="503",description="File Storage unavailable; old avatar preserved")
    })
    public ApiResponse<UserProfile> avatar(Authentication auth,@RequestPart("file") @Schema(type="string",format="binary") MultipartFile file) {
        return ApiResponse.success(service.avatar(id(auth),file));
    }
    @GetMapping("/{username}/profile")
    @Operation(summary="Read the public profile allowlist; hidden profiles return 404")
    public ApiResponse<PublicUserProfile> publicProfile(@PathVariable String username) { return ApiResponse.success(service.publicProfile(username)); }
    @GetMapping(value="/{username}/profile/avatar",produces={MediaType.IMAGE_PNG_VALUE,MediaType.IMAGE_JPEG_VALUE})
    @Operation(summary="Read avatar bytes subject to the same profile visibility rules")
    public ResponseEntity<Resource> image(@PathVariable String username,Authentication auth) {
        UUID viewer=auth==null?null:id(auth);
        Resource resource=service.avatarContent(username,viewer);
        MediaType type=resource.getFilename()!=null && resource.getFilename().endsWith(".png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).body(resource);
    }
    private UUID id(Authentication auth) { return UUID.fromString(auth.getName()); }
}
