package com.algolearner.platform.problem;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.storage.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Set;
import java.util.UUID;

class ProblemReportImageServiceTest {
    private final ProblemCatalogService catalog = mock(ProblemCatalogService.class);
    private final ProblemService problems = mock(ProblemService.class);
    private final FileRecordService files = mock(FileRecordService.class);
    private final FileValidator validator = mock(FileValidator.class);
    private final MalwareScanner scanner = mock(MalwareScanner.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PrivateObjectStorage objects = mock(PrivateObjectStorage.class);
    private final ProblemReportImageService images =
            new ProblemReportImageService(
                    catalog, problems, files, validator, scanner, jdbc, objects);

    @Test
    void oversizedImageIsRejectedBeforeScanningOrStorage() {
        var request = new ProblemDtos.ReportRequest(ProblemDtos.ReportReason.OTHER, "Typo");
        var image =
                new MockMultipartFile(
                        "image", "image.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertThatThrownBy(
                        () -> images.report(UUID.randomUUID(), UUID.randomUUID(), request, image))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(files, validator, scanner, jdbc, objects);
    }

    @Test
    void disabledImagePolicyRejectsUploadBeforeScanningOrStoringBytes() {
        when(files.uploadPolicy())
                .thenReturn(new FilePolicyService.Policy(1024, Set.of(FileKind.PDF)));
        var request = new ProblemDtos.ReportRequest(ProblemDtos.ReportReason.OTHER, "Typo");
        var image = new MockMultipartFile("image", "image.png", "image/png", new byte[] {1, 2});
        assertThatThrownBy(
                        () -> images.report(UUID.randomUUID(), UUID.randomUUID(), request, image))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(validator, scanner, jdbc);
    }

    @Test
    void storesOnlyPrivateFileReferenceAndCleansUpOnRollback() throws Exception {
        UUID id = UUID.randomUUID(), actor = UUID.randomUUID();
        when(catalog.report(any(), any(), any())).thenReturn(id);
        when(files.uploadPolicy())
                .thenReturn(new FilePolicyService.Policy(5242880, Set.of(FileKind.IMAGE)));
        when(validator.validate(any(), any(), anyLong()))
                .thenReturn(
                        new ValidatedFile(
                                "image.png", "image/png", FileKind.IMAGE, new byte[] {1, 2}));
        org.springframework.transaction.support.TransactionSynchronizationManager
                .initSynchronization();
        try {
            images.report(
                    actor,
                    UUID.randomUUID(),
                    new ProblemDtos.ReportRequest(ProblemDtos.ReportReason.OTHER, "Typo"),
                    new MockMultipartFile("image", "image.png", "image/png", new byte[] {1, 2}));
            var key = org.mockito.ArgumentCaptor.forClass(String.class);
            var order = inOrder(scanner, objects, jdbc);
            order.verify(scanner).requireClean(any());
            order.verify(objects).upload(key.capture(), eq("image/png"), any());
            order.verify(jdbc)
                    .update(
                            contains("image_file_id=:file,image_bytes=NULL"),
                            org.mockito.ArgumentMatchers.<java.util.Map<String, ?>>argThat(
                                    params ->
                                            params.containsKey("file")
                                                    && !params.containsKey("bytes")));
            for (var callback :
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .getSynchronizations())
                callback.afterCompletion(
                        org.springframework.transaction.support.TransactionSynchronization
                                .STATUS_ROLLED_BACK);
            verify(objects).delete(key.getValue());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .clearSynchronization();
        }
    }

    @Test
    void infectedImageNeverReachesStorage() throws Exception {
        when(files.uploadPolicy())
                .thenReturn(new FilePolicyService.Policy(5242880, Set.of(FileKind.IMAGE)));
        when(validator.validate(any(), any(), anyLong()))
                .thenReturn(
                        new ValidatedFile(
                                "image.png", "image/png", FileKind.IMAGE, new byte[] {1, 2}));
        doThrow(new IllegalStateException("Scan rejected")).when(scanner).requireClean(any());
        assertThatThrownBy(
                        () ->
                                images.report(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        new ProblemDtos.ReportRequest(
                                                ProblemDtos.ReportReason.OTHER, "Typo"),
                                        new MockMultipartFile(
                                                "image",
                                                "image.png",
                                                "image/png",
                                                new byte[] {1, 2})))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(objects, jdbc);
    }
}
