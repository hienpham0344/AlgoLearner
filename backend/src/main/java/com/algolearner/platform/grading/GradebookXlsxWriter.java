package com.algolearner.platform.grading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

final class GradebookXlsxWriter implements GradebookExportService.SheetWriter {
    private final Path path;
    private final SXSSFWorkbook workbook = new SXSSFWorkbook(100);
    private final org.apache.poi.xssf.streaming.SXSSFSheet sheet;
    private int nextRow;

    GradebookXlsxWriter(Path path) {
        this.path = path;
        workbook.setCompressTempFiles(true);
        sheet = workbook.createSheet("Gradebook");
    }

    @Override
    public void row(List<?> cells) {
        var row = sheet.createRow(nextRow++);
        for (int column = 0; column < cells.size(); column++) {
            var value = cells.get(column);
            if (value == null) continue;
            var cell = row.createCell(column);
            if (value instanceof Number number) cell.setCellValue(number.doubleValue());
            else if (value instanceof Boolean flag) cell.setCellValue(flag);
            else cell.setCellValue(value.toString());
        }
    }

    @Override
    public void finish() throws IOException {
        try (var output = Files.newOutputStream(path)) {
            workbook.write(output);
        }
    }

    @Override
    public void close() throws IOException {
        workbook.close();
    }
}
