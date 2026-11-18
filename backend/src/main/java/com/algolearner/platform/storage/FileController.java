package com.algolearner.platform.storage;

import com.algolearner.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@Profile("!mock")
@RequiredArgsConstructor
@Tag(name = "Private learning files")
@SecurityRequirement(name = "bearerAuth")
public class FileController {
    private final ManagedFileService files;

    @PostMapping(value = "/api/v1/classrooms/{classId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload and scan a private classroom file", description = "Returns READY only after structural validation, private storage and malware scan succeed. Requires owner or active Student according to purpose; provider failure returns 503.")
    public ApiResponse<FileMetadata> upload(Authentication authentication, @PathVariable UUID classId,
            @RequestParam FilePurpose purpose, @RequestPart MultipartFile file) {
        return ApiResponse.success(files.upload(actor(authentication), classId, purpose, file));
    }

    @GetMapping("/api/v1/files/{fileId}")
    @Operation(summary = "Read authorized file metadata", description = "Returns 403 for private or inaccessible files and 404 for unknown IDs; object keys and service credentials are never returned.")
    public ApiResponse<FileMetadata> metadata(Authentication authentication, @PathVariable UUID fileId) {
        return ApiResponse.success(files.read(actor(authentication), fileId));
    }

    @GetMapping("/api/v1/files/{fileId}/content")
    @Operation(summary = "Download an authorized private file", description = "Checks current account/classroom permissions; streams an attachment without exposing a public object URL. Pending or rejected files return 409.")
    public ResponseEntity<StreamingResponseBody> content(Authentication authentication, @PathVariable UUID fileId) {
        UUID actor = actor(authentication);
        var file = files.read(actor, fileId);
        if (file.state() != FileState.READY) {
            throw new StorageException("FILE_NOT_READY", HttpStatus.CONFLICT, "File is not ready");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType())).contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(output -> files.download(actor, fileId, output));
    }

    private UUID actor(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
