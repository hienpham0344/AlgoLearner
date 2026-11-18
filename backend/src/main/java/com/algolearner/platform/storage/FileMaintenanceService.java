package com.algolearner.platform.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class FileMaintenanceService {
    private static final Logger LOG = LoggerFactory.getLogger(FileMaintenanceService.class);
    private final FileRecordService records;
    private final PrivateObjectStorage objects;
    private final MalwareScanner scanner;

    public void recoverPending() {
        for (var id : records.recoveryCandidates()) {
            var ticket = records.claimRecovery(id);
            if (ticket == null) continue;
            try {
                var bytes = download(ticket);
                scanner.requireClean(bytes);
                records.finishRecovery(ticket, true, false);
            } catch (StorageException error) {
                records.finishRecovery(ticket, false, error.getErrorCode().equals("FILE_INFECTED"));
                LOG.warn("File recovery deferred: id={}, code={}", id, error.getErrorCode());
            } catch (IOException error) {
                records.finishRecovery(ticket, false, false);
                LOG.warn("File recovery deferred: id={}, code=FILE_READ_FAILED", id);
            }
        }
    }

    public void cleanOrphans() {
        for (var id : records.cleanupCandidates()) {
            var ticket = records.claimDeletion(id);
            if (ticket == null) continue;
            try {
                objects.delete(ticket.objectKey());
                records.finishDeletion(ticket);
            } catch (StorageException error) {
                LOG.warn("File cleanup deferred: id={}, code={}", id, error.getErrorCode());
            }
        }
    }

    private byte[] download(FileRecordService.ProcessingTicket ticket) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var bounded = new OutputStream() {
            @Override public void write(int value) throws IOException {
                if (bytes.size() >= ticket.size()) throw new IOException("Stored file exceeds recorded size");
                bytes.write(value);
            }
            @Override public void write(byte[] buffer, int offset, int length) throws IOException {
                if ((long) bytes.size() + length > ticket.size()) throw new IOException("Stored file exceeds recorded size");
                bytes.write(buffer, offset, length);
            }
        };
        objects.download(ticket.objectKey(), bounded);
        byte[] content = bytes.toByteArray();
        try {
            if (content.length != ticket.size() || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)).equals(ticket.sha256())) {
                throw new IOException("Stored file checksum mismatch");
            }
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
        return content;
    }
}
