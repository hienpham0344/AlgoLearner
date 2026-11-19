package com.algolearner.platform.storage;

import java.time.Instant;
import java.util.UUID;

public record FileMetadata(UUID id, String name, String mediaType, FileKind kind, long size,
        FilePurpose purpose, FileState state, String sha256, Instant createdAt) {
}
