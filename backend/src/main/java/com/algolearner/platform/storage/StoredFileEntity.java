package com.algolearner.platform.storage;

import com.algolearner.platform.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_stored_file")
@Getter @Setter @NoArgsConstructor
public class StoredFileEntity extends BaseAuditableEntity {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @Column(name = "uploader_id", nullable = false) private UUID uploaderId;
    @Column(name = "classroom_id", nullable = false) private UUID classroomId;
    @Column(name = "object_key", nullable = false, unique = true, length = 80) private String objectKey;
    @Column(name = "file_name", nullable = false, length = 255) private String name;
    @Column(name = "media_type", nullable = false, length = 150) private String mediaType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FileKind kind;
    @Column(name = "byte_size", nullable = false) private long size;
    @Column(nullable = false, length = 64) private String sha256;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private FilePurpose purpose;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FileState state;
    @Column(name = "object_uploaded", nullable = false) private boolean objectUploaded;
    @Column(name = "scan_attempts", nullable = false) private int scanAttempts;
    @Column(name = "processing_token") private UUID processingToken;
    @Column(name = "processing_until") private Instant processingUntil;
    @Version @Column(nullable = false) private long version;

    public FileMetadata metadata() {
        return new FileMetadata(id, name, mediaType, kind, size, purpose, state, sha256, getCreatedAt());
    }
}
