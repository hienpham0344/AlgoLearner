package com.algolearner.platform.problem;

import com.algolearner.platform.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@Profile("!mock")
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class ProblemReportImageController {
    private final ProblemReportImageService images;

    @PostMapping(value = "/problems/{id}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a problem with an optional screenshot")
    public ApiResponse<UUID> report(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestPart("report") ProblemDtos.ReportRequest report,
            @RequestPart(value = "image", required = false) MultipartFile image)
            throws IOException {
        return ApiResponse.success(
                images.report(UUID.fromString(auth.getName()), id, report, image));
    }

    @GetMapping("/admin/problems/reports/{id}/image")
    @Operation(summary = "Read the screenshot attached to a problem report")
    public ResponseEntity<byte[]> image(Authentication auth, @PathVariable UUID id)
            throws IOException {
        var image = images.image(UUID.fromString(auth.getName()), id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-image\"")
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .body(image.bytes());
    }
}
