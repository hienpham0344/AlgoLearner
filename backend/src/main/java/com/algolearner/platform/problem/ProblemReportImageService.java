package com.algolearner.platform.problem;

import com.algolearner.platform.storage.*;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemReportImageService {
    private final ProblemCatalogService catalog;
    private final ProblemService problems;
    private final FileRecordService files;
    private final FileValidator validator;
    private final MalwareScanner scanner;
    private final NamedParameterJdbcTemplate jdbc;
    private final PrivateObjectStorage objects;

    @org.springframework.beans.factory.annotation.Value(
            "${app.problems.report-image-max-bytes:5242880}")
    private long maxBytes = 5 * 1024 * 1024;

    @Transactional(rollbackFor = IOException.class)
    public UUID report(
            UUID actor, UUID problem, ProblemDtos.ReportRequest request, MultipartFile image)
            throws IOException {
        UUID id = catalog.report(actor, problem, request);
        if (image == null) return id;
        long limit = Math.min(maxBytes, 5 * 1024 * 1024);
        if (image.getSize() > limit)
            throw ProblemService.failure(HttpStatus.PAYLOAD_TOO_LARGE, "REPORT_IMAGE_TOO_LARGE");
        var policy = files.uploadPolicy();
        if (!policy.allowedKinds().contains(FileKind.IMAGE))
            throw ProblemService.failure(HttpStatus.BAD_REQUEST, "IMAGE_NOT_ALLOWED");
        try (var input = image.getInputStream()) {
            var file =
                    validator.validate(
                            image.getOriginalFilename(), input, Math.min(limit, policy.maxBytes()));
            if (file.kind() != FileKind.IMAGE)
                throw ProblemService.failure(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED");
            byte[] bytes = file.bytes();
            scanner.requireClean(bytes);
            UUID fileId = UUID.randomUUID();
            String key = actor + "/" + fileId;
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(
                            new org.springframework.transaction.support
                                    .TransactionSynchronization() {
                                @Override
                                public void afterCompletion(int status) {
                                    if (status != STATUS_COMMITTED) {
                                        try {
                                            objects.delete(key);
                                        } catch (RuntimeException error) {
                                            org.slf4j.LoggerFactory.getLogger(
                                                            ProblemReportImageService.class)
                                                    .warn(
                                                            "Report image rollback cleanup failed"
                                                                + " for file {}",
                                                            fileId);
                                        }
                                    }
                                }
                            });
            objects.upload(key, file.mediaType(), bytes);
            jdbc.update(
                    "UPDATE tbl_problem_report SET"
                        + " image_file_id=:file,image_bytes=NULL,image_type=:type WHERE id=:id",
                    Map.of("id", id, "file", fileId, "type", file.mediaType()));
        }
        return id;
    }

    public record Image(String mediaType, byte[] bytes) {}

    public Image image(UUID admin, UUID report) throws IOException {
        problems.requireRole(admin, "ADMIN");
        var rows =
                jdbc.query(
                        "SELECT image_type,image_bytes,image_file_id,reporter_id FROM"
                            + " tbl_problem_report WHERE id=:id AND (image_file_id IS NOT NULL OR"
                            + " image_bytes IS NOT NULL)",
                        Map.of("id", report),
                        (rs, n) ->
                                new StoredImage(
                                        rs.getString(1),
                                        rs.getBytes(2),
                                        rs.getObject(3, UUID.class),
                                        rs.getObject(4, UUID.class)));
        if (rows.isEmpty())
            throw ProblemService.failure(HttpStatus.NOT_FOUND, "REPORT_IMAGE_NOT_FOUND");
        var stored = rows.getFirst();
        if (stored.fileId() == null) return new Image(stored.mediaType(), stored.legacyBytes());
        var output = new java.io.ByteArrayOutputStream();
        objects.download(
                stored.owner() + "/" + stored.fileId(),
                new java.io.OutputStream() {
                    private int count;

                    @Override
                    public void write(int value) throws IOException {
                        if (++count > 5 * 1024 * 1024)
                            throw new IOException("Report image exceeds size limit");
                        output.write(value);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        if (length > 5 * 1024 * 1024 - count)
                            throw new IOException("Report image exceeds size limit");
                        count += length;
                        output.write(bytes, offset, length);
                    }
                });
        return new Image(stored.mediaType(), output.toByteArray());
    }

    private record StoredImage(String mediaType, byte[] legacyBytes, UUID fileId, UUID owner) {}
}
