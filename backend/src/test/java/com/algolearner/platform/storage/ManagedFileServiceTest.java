package com.algolearner.platform.storage;

import com.algolearner.platform.common.exception.AppException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManagedFileServiceTest {
    private final FileRecordService records = mock(FileRecordService.class);
    private final PrivateObjectStorage objects = mock(PrivateObjectStorage.class);
    private final MalwareScanner scanner = mock(MalwareScanner.class);
    private final UUID actor = UUID.randomUUID();
    private final UUID classroom = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private final ManagedFileService files = new ManagedFileService(records, objects, scanner, new FileValidator(), 1024);
    private StoredFileEntity stored;

    @BeforeEach
    void setUp() {
        stored = new StoredFileEntity();
        stored.setId(id);
        stored.setObjectKey(actor + "/" + id);
        stored.setState(FileState.PENDING);
        when(records.begin(eq(actor), eq(classroom), eq(FilePurpose.LESSON_MATERIAL), any(), anyString())).thenReturn(stored);
        when(records.ready(actor, id)).thenReturn(new FileMetadata(id, "notes.pdf", "application/pdf", FileKind.PDF,
                30, FilePurpose.LESSON_MATERIAL, FileState.READY, "checksum", null));
    }

    @Test
    void marksReadyOnlyAfterPrivateUploadAndSuccessfulScan() {
        assertThat(files.upload(actor, classroom, FilePurpose.LESSON_MATERIAL, pdf()).state()).isEqualTo(FileState.READY);
        var order = inOrder(records, objects, scanner);
        order.verify(records).authorizeUpload(actor, classroom, FilePurpose.LESSON_MATERIAL);
        order.verify(records).begin(eq(actor), eq(classroom), eq(FilePurpose.LESSON_MATERIAL), any(), matches("[a-f0-9]{64}"));
        order.verify(objects).upload(eq(actor + "/" + id), eq("application/pdf"), any());
        order.verify(records).uploaded(id);
        order.verify(scanner).requireClean(any());
        order.verify(records).ready(actor, id);
    }

    @Test
    void scannerOutageKeepsUploadPendingAndDoesNotClaimClean() {
        doThrow(new StorageException("FILE_SCAN_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "Scan unavailable"))
                .when(scanner).requireClean(any());
        assertThatThrownBy(() -> files.upload(actor, classroom, FilePurpose.LESSON_MATERIAL, pdf()))
                .isInstanceOf(AppException.class);
        verify(records, never()).ready(any(), any());
        verify(records, never()).rejected(any());
    }

    @Test
    void infectedFileIsRejectedAndCannotBecomeReady() {
        doThrow(new StorageException("FILE_INFECTED", HttpStatus.UNPROCESSABLE_ENTITY, "File rejected"))
                .when(scanner).requireClean(any());
        assertThatThrownBy(() -> files.upload(actor, classroom, FilePurpose.LESSON_MATERIAL, pdf()))
                .isInstanceOf(AppException.class);
        verify(records).rejected(id);
        verify(records, never()).ready(any(), any());
    }

    @Test
    void unauthorizedUploadNeverContactsProviderOrScanner() {
        doThrow(new StorageException("FORBIDDEN", HttpStatus.FORBIDDEN, "Denied"))
                .when(records).authorizeUpload(actor, classroom, FilePurpose.LESSON_MATERIAL);
        assertThatThrownBy(() -> files.upload(actor, classroom, FilePurpose.LESSON_MATERIAL, pdf()))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(objects, scanner);
        verify(records, never()).begin(any(), any(), any(), any(), any());
    }

    @Test
    void capabilitiesExposeAvailabilityLimitAndDeterministicExtensions() {
        when(objects.isAvailable()).thenReturn(true);
        when(scanner.isAvailable()).thenReturn(true);
        when(records.uploadPolicy()).thenReturn(new FilePolicyService.Policy(
                20_971_520L, java.util.Set.of(FileKind.PDF, FileKind.IMAGE, FileKind.ZIP)));

        var caps = files.capabilities();
        assertThat(caps.uploadsAvailable()).isTrue();
        assertThat(caps.maxFileSizeBytes()).isEqualTo(1024L);
        assertThat(caps.allowedExtensions()).containsExactly("pdf", "zip", "png", "jpg", "jpeg");

        when(scanner.isAvailable()).thenReturn(false);
        assertThat(files.capabilities().uploadsAvailable()).isFalse();

        when(scanner.isAvailable()).thenReturn(true);
        when(records.uploadPolicy()).thenReturn(new FilePolicyService.Policy(0L, java.util.Set.of(FileKind.PDF)));
        assertThat(files.capabilities().uploadsAvailable()).isFalse();
        assertThat(files.capabilities().maxFileSizeBytes()).isZero();

        when(records.uploadPolicy()).thenReturn(new FilePolicyService.Policy(1024L, null));
        var noKinds = files.capabilities();
        assertThat(noKinds.uploadsAvailable()).isFalse();
        assertThat(noKinds.allowedExtensions()).isEmpty();
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "notes.pdf", "text/html", LearningFileFixtures.pdf());
    }
}
