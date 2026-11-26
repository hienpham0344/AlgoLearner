package com.algolearner.platform.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

final class GradebookCsvWriter implements GradebookExportService.SheetWriter {
    private final CSVPrinter printer;

    GradebookCsvWriter(Path path) throws IOException {
        printer = CSVFormat.RFC4180.print(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
    }

    @Override
    public void row(List<?> cells) throws IOException {
        printer.printRecord(cells.stream().map(value -> value instanceof String text ? safeText(text) : value).toList());
    }

    private static String safeText(String value) {
        if (value.isEmpty()) return value;
        var stripped = value.stripLeading();
        if (Character.isISOControl(value.codePointAt(0)) || !stripped.isEmpty()
                && ("=+-@".indexOf(stripped.charAt(0)) >= 0 || Character.isISOControl(stripped.codePointAt(0)))) {
            return "'" + value;
        }
        return value;
    }

    @Override
    public void finish() throws IOException {
        printer.flush();
    }

    @Override
    public void close() throws IOException {
        printer.close();
    }
}
