package com.algolearner.platform.storage;

import com.algolearner.platform.common.exception.AppException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidatorTest {
    private final FileValidator validator = new FileValidator();

    @Test
    void detectsPdfFromBytesAndSanitizesDownloadName() throws Exception {
        var file = validate("../../lecture.pdf", LearningFileFixtures.pdf());
        assertThat(file.kind()).isEqualTo(FileKind.PDF);
        assertThat(file.mediaType()).isEqualTo("application/pdf");
        assertThat(file.name()).isEqualTo("lecture.pdf");
    }

    @Test
    void rejectsExecutableRenamedAsPdf() {
        assertThatThrownBy(() -> validate("notes.pdf", "MZ executable".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(AppException.class).hasMessageContaining("type");
    }

    @Test
    void measuresActualStreamRatherThanDeclaredSize() {
        assertThatThrownBy(() -> validator.validate("large.zip", new ByteArrayInputStream(new byte[11]), 10))
                .isInstanceOf(AppException.class).hasMessageContaining("limit");
    }

    @Test
    void detectsOoxmlAndRejectsMacrosInsteadOfAcceptingItAsGenericZip() throws Exception {
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put("[Content_Types].xml", ("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>").getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", "<document/>".getBytes(StandardCharsets.UTF_8));
        assertThat(validate("document.docx", zip(entries)).kind()).isEqualTo(FileKind.DOCX);
        entries.put("word/vbaProject.bin", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> validate("document.docx", zip(entries)))
                .isInstanceOf(AppException.class).hasMessageContaining("macro");
    }

    @Test
    void rejectsArchiveTraversalAndDecompressionBomb() throws Exception {
        assertThatThrownBy(() -> validate("archive.zip", zip(Map.of("../escape", new byte[] {1}))))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> validate("archive.zip", zip(Map.of("large.txt", new byte[2 * 1024 * 1024]))))
                .isInstanceOf(AppException.class).hasMessageContaining("expansion");
    }

    @Test
    void rejectsHtmlAndActivePdf() {
        assertThatThrownBy(() -> validate("photo.png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> validate("notes.pdf", "%PDF-1.7\n/JavaScript (evil)\n%%EOF".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void rejectsMalformedPdfAndEscapedActiveDictionaryNames() {
        assertThatThrownBy(() -> validate("notes.pdf", "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> validate("notes.pdf", LearningFileFixtures.escapedActivePdf()))
                .isInstanceOf(AppException.class);
    }

    private ValidatedFile validate(String name, byte[] bytes) throws Exception {
        return validator.validate(name, new ByteArrayInputStream(bytes), 20 * 1024 * 1024);
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
