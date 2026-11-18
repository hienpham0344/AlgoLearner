package com.algolearner.platform.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

final class LearningFileFixtures {
    private LearningFileFixtures() { }

    static byte[] pdf() {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Could not create PDF fixture", error);
        }
    }

    static byte[] escapedActivePdf() {
        var objects = java.util.List.of(
                "<< /Type /Catalog /Pages 2 0 R /Open#41ction 4 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>",
                "<< /S /Java#53cript /JS (app.alert\\(1\\)) >>");
        var output = new StringBuilder("%PDF-1.7\n");
        var offsets = new ArrayList<Integer>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.length());
            output.append(index + 1).append(" 0 obj\n").append(objects.get(index)).append("\nendobj\n");
        }
        int xref = output.length();
        output.append("xref\n0 5\n0000000000 65535 f \n");
        offsets.forEach(offset -> output.append(String.format(java.util.Locale.ROOT,"%010d 00000 n \n", offset)));
        output.append("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return output.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
