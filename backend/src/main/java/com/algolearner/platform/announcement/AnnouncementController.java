package com.algolearner.platform.announcement;

import com.algolearner.platform.common.dto.ApiResponse;
import com.algolearner.platform.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/classrooms/{classId}/announcements")
@RequiredArgsConstructor
@Tag(name = "Classroom Announcements", description = "Classroom Announcements API (Module 4.4)")
@SecurityRequirement(name = "bearerAuth")
public class AnnouncementController {

    private final AnnouncementService service;

    @GetMapping
    @Operation(summary = "List classroom announcements sorted by pinned first, then creation date")
    public PageResponse<AnnouncementDtos.AnnouncementView> list(
            Authentication auth,
            @PathVariable UUID classId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return service.list(actor(auth), classId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new classroom announcement (Teacher owner only)")
    public ApiResponse<AnnouncementDtos.AnnouncementView> create(
            Authentication auth,
            @PathVariable UUID classId,
            @Valid @RequestBody AnnouncementDtos.CreateRequest request
    ) {
        return ApiResponse.success(service.create(actor(auth), classId, request));
    }

    @GetMapping("/{announcementId}")
    @Operation(summary = "Get announcement details")
    public ApiResponse<AnnouncementDtos.AnnouncementView> get(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID announcementId
    ) {
        return ApiResponse.success(service.get(actor(auth), classId, announcementId));
    }

    @PutMapping("/{announcementId}")
    @Operation(summary = "Update an announcement (Teacher owner only)")
    public ApiResponse<AnnouncementDtos.AnnouncementView> update(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID announcementId,
            @Valid @RequestBody AnnouncementDtos.UpdateRequest request
    ) {
        return ApiResponse.success(service.update(actor(auth), classId, announcementId, request));
    }

    @DeleteMapping("/{announcementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an announcement (Teacher owner only)")
    public void delete(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID announcementId
    ) {
        service.delete(actor(auth), classId, announcementId);
    }

    @PatchMapping("/{announcementId}/pin")
    @Operation(summary = "Pin an announcement (Teacher owner only)")
    public ApiResponse<AnnouncementDtos.AnnouncementView> pin(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID announcementId
    ) {
        return ApiResponse.success(service.pin(actor(auth), classId, announcementId));
    }

    @PatchMapping("/{announcementId}/unpin")
    @Operation(summary = "Unpin an announcement (Teacher owner only)")
    public ApiResponse<AnnouncementDtos.AnnouncementView> unpin(
            Authentication auth,
            @PathVariable UUID classId,
            @PathVariable UUID announcementId
    ) {
        return ApiResponse.success(service.unpin(actor(auth), classId, announcementId));
    }

    private UUID actor(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
