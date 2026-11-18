package com.algolearner.platform.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.HttpStatus;

public final class FileValidator {
    private static final long MAX_PIXELS = 16_000_000;
    private static final long MAX_EXPANDED_BYTES = 50L * 1024 * 1024;
    private static final int MAX_ENTRIES = 1000;

    public ValidatedFile validate(String name, InputStream input, long maxBytes) throws IOException {
        if (maxBytes < 1 || maxBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid upload limit");
        }
        byte[] bytes = input.readNBytes((int) maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new StorageException("FILE_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds upload limit");
        }
        if (bytes.length == 0) throw invalid("File is empty");
        String safeName = safeName(name);
        if (startsWith(bytes, "%PDF-")) {
            new PdfFileInspector().validate(bytes);
            requireExtension(safeName, ".pdf");
            return new ValidatedFile(safeName, "application/pdf", FileKind.PDF, bytes);
        }
        if (isImage(bytes)) return image(safeName, bytes, maxBytes);
        if (startsWith(bytes, "PK\u0003\u0004")) return archive(safeName, bytes);
        throw invalid("Unsupported file type");
    }

    private ValidatedFile image(String name, byte[] bytes, long maxBytes) throws IOException {
        try (var imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw invalid("Unsupported image type");
            var reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String extension = switch (format) {
                    case "jpeg", "jpg" -> "jpg";
                    case "png" -> "png";
                    default -> throw invalid("Unsupported image type");
                };
                if (extension.equals("jpg")) requireExtension(name, ".jpg", ".jpeg");
                else requireExtension(name, ".png");
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw invalid("Image dimensions exceed limit");
                }
                var decoded = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(decoded, extension, output)) throw invalid("Unsupported image type");
                if (output.size() > maxBytes) throw invalid("Normalized image exceeds upload limit");
                return new ValidatedFile(name, "image/" + (extension.equals("jpg") ? "jpeg" : "png"),
                        FileKind.IMAGE, output.toByteArray());
            } finally {
                reader.dispose();
            }
        } catch (javax.imageio.IIOException error) {
            throw invalid("Invalid image content");
        }
    }

    private ValidatedFile archive(String name, byte[] bytes) throws IOException {
        Set<String> entries = new HashSet<>();
        long expanded = 0;
        byte[] contentTypes = null;
        byte[] buffer = new byte[8192];
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String path = entry.getName().replace('\\', '/');
                if (path.startsWith("/") || path.contains("\u0000") || path.matches("^[A-Za-z]:.*")
                        || java.util.Arrays.asList(path.split("/")).contains("..") || !entries.add(path)) {
                    throw invalid("Unsafe archive path");
                }
                if (entries.size() > MAX_ENTRIES) throw invalid("Archive entry limit exceeded");
                String lower = path.toLowerCase(Locale.ROOT);
                if (lower.contains("vbaproject") || lower.endsWith(".vbs") || lower.endsWith(".vba")) {
                    throw invalid("Office macros are not allowed");
                }
                var manifest = path.equals("[Content_Types].xml") ? new ByteArrayOutputStream() : null;
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    if (expanded > MAX_EXPANDED_BYTES || expanded > (long) bytes.length * 100 + 1024) {
                        throw invalid("Archive expansion limit exceeded");
                    }
                    if (manifest != null) {
                        if (manifest.size() + count > 1024 * 1024) throw invalid("Office manifest limit exceeded");
                        manifest.write(buffer, 0, count);
                    }
                }
                if (manifest != null) contentTypes = manifest.toByteArray();
                zip.closeEntry();
            }
        } catch (java.util.zip.ZipException error) {
            throw invalid("Invalid archive content");
        }
        if (entries.isEmpty()) throw invalid("Archive has no content");
        if (contentTypes != null) {
            String manifest = officeManifest(contentTypes);
            if (manifest.contains("macroEnabled") || manifest.contains("vbaProject")) {
                throw invalid("Office macros are not allowed");
            }
            if (entries.contains("word/document.xml") && manifest.contains("wordprocessingml.document.main+xml")) {
                requireExtension(name, ".docx");
                return new ValidatedFile(name, "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        FileKind.DOCX, bytes);
            }
            if (entries.contains("ppt/presentation.xml") && manifest.contains("presentationml.presentation.main+xml")) {
                requireExtension(name, ".pptx");
                return new ValidatedFile(name, "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        FileKind.PPTX, bytes);
            }
            throw invalid("Unsupported Office file type");
        }
        requireExtension(name, ".zip");
        return new ValidatedFile(name, "application/zip", FileKind.ZIP, bytes);
    }

    private String officeManifest(byte[] bytes) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw invalid("Invalid Office manifest");
        }
    }

    private void requireExtension(String name, String... extensions) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : extensions) if (lower.endsWith(extension)) return;
        throw invalid("File name does not match detected type");
    }

    private String safeName(String name) {
        String value = name == null ? "upload" : name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (value.isEmpty() || value.length() > 255) throw invalid("Invalid file name");
        return value;
    }

    private boolean startsWith(byte[] bytes, String signature) {
        byte[] prefix = signature.getBytes(StandardCharsets.ISO_8859_1);
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    private boolean isImage(byte[] bytes) {
        return startsWith(bytes, "\u0089PNG\r\n\u001a\n")
                || bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
    }

    private StorageException invalid(String message) {
        return new StorageException("INVALID_FILE", HttpStatus.BAD_REQUEST, message);
    }
}
