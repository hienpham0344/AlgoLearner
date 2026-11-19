package com.algolearner.platform.storage;

import java.util.UUID;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("!mock")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ManagedFileService {
    private final FileRecordService records;
    private final PrivateObjectStorage objects;
    private final MalwareScanner scanner;
    private final FileValidator validator;
    private final long maxBytes;

    public ManagedFileService(FileRecordService records, PrivateObjectStorage objects,
            MalwareScanner scanner, FileValidator validator, @Value("${app.files.max-bytes:20971520}") long maxBytes) {
        if (maxBytes < 1 || maxBytes > 20L * 1024 * 1024) throw new IllegalArgumentException("Invalid upload limit");
        this.records = records;
        this.objects = objects;
        this.scanner = scanner;
        this.validator = validator;
        this.maxBytes = maxBytes;
    }

    public record UploadCapabilities(boolean uploadsAvailable, long maxFileSizeBytes, List<String> allowedExtensions) { }

    public UploadCapabilities capabilities() {
        var policy = records.uploadPolicy();
        if (policy == null) {
            return new UploadCapabilities(false, 0L, List.of());
        }
        long effectiveMax = Math.max(0L, Math.min(maxBytes, policy.maxBytes()));
        var allowedKinds = policy.allowedKinds() != null ? policy.allowedKinds() : java.util.Set.<FileKind>of();
        var extensions = new ArrayList<String>();
        for (var kind : FileKind.values()) {
            if (!allowedKinds.contains(kind)) continue;
            switch (kind) {
                case PDF -> extensions.add("pdf");
                case DOCX -> extensions.add("docx");
                case PPTX -> extensions.add("pptx");
                case ZIP -> extensions.add("zip");
                case IMAGE -> extensions.addAll(List.of("png", "jpg", "jpeg"));
            }
        }
        boolean uploadsAvailable = objects.isAvailable() && scanner.isAvailable()
                && effectiveMax > 0 && !extensions.isEmpty();
        return new UploadCapabilities(uploadsAvailable, effectiveMax, List.copyOf(extensions));
    }

    public FileMetadata upload(UUID actor, UUID classroom, FilePurpose purpose, MultipartFile file) {
        records.authorizeUpload(actor, classroom, purpose);
        final ValidatedFile validated;
        try (var input = file.getInputStream()) {
            validated = validator.validate(file.getOriginalFilename(), input, maxBytes);
        } catch (IOException error) {
            throw new StorageException("FILE_READ_FAILED", HttpStatus.BAD_REQUEST, "Uploaded file could not be read");
        }
        byte[] bytes = validated.bytes();
        var stored = records.begin(actor, classroom, purpose, validated, checksum(bytes));
        objects.upload(stored.getObjectKey(), validated.mediaType(), bytes);
        records.uploaded(stored.getId());
        try {
            scanner.requireClean(bytes);
        } catch (StorageException error) {
            if (error.getErrorCode().equals("FILE_INFECTED")) records.rejected(stored.getId());
            throw error;
        }
        return records.ready(actor, stored.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public FileMetadata requireReady(UUID actor, UUID classroom, UUID fileId, FilePurpose purpose) {
        return records.requireReady(actor, classroom, fileId, purpose);
    }

    public FileMetadata read(UUID actor, UUID fileId) {
        return records.read(actor, fileId);
    }

    public void deleteUnreferenced(UUID actor, UUID fileId) {
        var ticket = records.claimImmediateDeletion(actor, fileId);
        if (ticket.objectUploaded()) {
            objects.delete(ticket.objectKey());
        }
        records.finishDeletion(ticket);
    }

    public void download(UUID actor, UUID fileId, OutputStream output) throws IOException {
        var file = records.downloadable(actor, fileId);
        var bounded = new OutputStream() {
            long written;
            @Override public void write(int value) throws IOException {
                if (++written > file.getSize()) throw new IOException("Stored file exceeds recorded size");
                output.write(value);
            }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                if (written + length > file.getSize()) throw new IOException("Stored file exceeds recorded size");
                output.write(bytes, offset, length);
                written += length;
            }
        };
        objects.download(file.getObjectKey(), bounded);
    }

    private String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
