package com.algolearner.platform.storage;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.springframework.http.HttpStatus;

/** Parses PDF names before checking them; escaped names and compressed dictionaries cannot bypass the gate. */
final class PdfFileInspector {
    private static final int MAX_OBJECTS = 100_000;
    private static final Set<String> ACTIVE_NAMES = Set.of("javascript", "js", "openaction", "aa", "launch",
            "embeddedfile", "embeddedfiles", "richmedia", "xfa", "submitform", "importdata", "gotor", "gotoe");

    void validate(byte[] bytes) {
        try (var input = new RandomAccessReadBuffer(bytes)) {
            var parser = new PDFParser(input, "", null, null, IOUtils.createTempFileOnlyStreamCache());
            try (var document = parser.parse(false)) {
                if (document.isEncrypted() || document.getNumberOfPages() < 1 || document.getNumberOfPages() > 1000) {
                    throw invalid();
                }
                var visited = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
                var queue = new ArrayDeque<COSBase>();
                queue.add(document.getDocument().getTrailer());
                while (!queue.isEmpty()) {
                    COSBase value = queue.removeFirst();
                    if (!visited.add(value)) continue;
                    if (visited.size() > MAX_OBJECTS || queue.size() > MAX_OBJECTS) throw invalid();
                    if (value instanceof COSName name) {
                        requirePassiveName(name);
                    } else if (value instanceof COSObject reference) {
                        if (reference.getObject() != null) queue.add(reference.getObject());
                    } else if (value instanceof COSDictionary dictionary) {
                        if (dictionary.size() > MAX_OBJECTS) throw invalid();
                        for (var entry : dictionary.entrySet()) {
                            requirePassiveName(entry.getKey());
                            if (entry.getValue() != null) queue.add(entry.getValue());
                        }
                    } else if (value instanceof COSArray array) {
                        if (array.size() > MAX_OBJECTS) throw invalid();
                        for (COSBase element : array) if (element != null) queue.add(element);
                    }
                }
            }
        } catch (IOException | IllegalArgumentException error) {
            throw invalid();
        }
    }

    private void requirePassiveName(COSName name) {
        if (ACTIVE_NAMES.contains(name.getName().toLowerCase(Locale.ROOT))) throw invalid();
    }

    private StorageException invalid() {
        return new StorageException("INVALID_FILE", HttpStatus.BAD_REQUEST, "Invalid or active PDF content");
    }
}
