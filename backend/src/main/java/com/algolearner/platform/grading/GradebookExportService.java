package com.algolearner.platform.grading;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.audit.AuditService.RequestContext;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.grading.GradebookDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Profile("!mock")
@RequiredArgsConstructor
public class GradebookExportService {
    private static final int BASE_COLUMNS = 4;
    private static final int COLUMNS_PER_ITEM = 4;
    private static final int XLSX_ROWS = 1_048_575;
    private static final int XLSX_ITEMS = (16_384 - BASE_COLUMNS) / COLUMNS_PER_ITEM;
    private static final int PAGE_SIZE = 100;

    private final GradebookAccess access;
    private final GradebookSchemeService schemes;
    private final GradebookReadRepository reads;
    private final GradebookQueryService queries;
    private final AuditService audit;
    private final ObjectMapper json;

    @Value("${app.gradebook.export.max-students:10000}")
    private int maxStudents;

    @Value("${app.gradebook.export.max-items:200}")
    private int maxItems;

    enum Format {
        CSV("text/csv;charset=UTF-8"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private final MediaType mediaType;

        Format(String mediaType) {
            this.mediaType = MediaType.parseMediaType(mediaType);
        }

        static Format parse(String format) {
            return switch (format.toLowerCase(Locale.ROOT)) {
                case "csv" -> CSV;
                case "xlsx" -> XLSX;
                default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "Format must be csv or xlsx");
            };
        }
    }

    interface SheetWriter extends AutoCloseable {
        void row(List<?> cells) throws IOException;
        void finish() throws IOException;
        @Override void close() throws IOException;
    }

    record Download(Path path, Format format, UUID schemeRevision) implements AutoCloseable {
        String filename(UUID classroom) {
            return "gradebook-" + classroom + "." + format.name().toLowerCase(Locale.ROOT);
        }

        MediaType mediaType() {
            return format.mediaType;
        }

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }

    record ExportAudit(String scope, UUID schemeRevisionId, String format, long rows, int items) { }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = IOException.class)
    public Download export(Authentication principal, UUID classroom, String requestedFormat, RequestContext request) throws IOException {
        var actor = access.requireTeacherRead(principal, classroom);
        var format = Format.parse(requestedFormat);
        var gradebook = schemes.find(classroom);
        var scheme = schemes.active(gradebook);
        var catalog = reads.items(classroom);
        var roster = reads.roster(classroom, PageRequest.of(0, PAGE_SIZE));
        int rowLimit = format == Format.XLSX ? Math.min(maxStudents, XLSX_ROWS) : maxStudents;
        int itemLimit = format == Format.XLSX ? Math.min(maxItems, XLSX_ITEMS) : maxItems;
        if (roster.getTotalElements() > rowLimit || catalog.size() > itemLimit) {
            throw new ApiException(ErrorCode.GRADEBOOK_EXPORT_LIMIT_EXCEEDED,
                    "Export supports at most " + rowLimit + " students and " + itemLimit + " items");
        }

        var path = Files.createTempFile("gradebook-", "." + format.name().toLowerCase(Locale.ROOT));
        var file = new Download(path, format, scheme.revisionId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        file.close();
                    } catch (IOException error) {
                        org.slf4j.LoggerFactory.getLogger(GradebookExportService.class)
                                .warn("Could not clean up the temporary gradebook export", error);
                    }
                }
            }
        });

        try (SheetWriter writer = format == Format.CSV ? new GradebookCsvWriter(path) : new GradebookXlsxWriter(path)) {
            writer.row(header(catalog));
            for (;;) {
                var studentIds = roster.stream().map(StudentIdentity::studentId).toList();
                var batch = queries.load(classroom, catalog, studentIds, GradeSourceResult.Audience.TEACHER)
                        .stream().collect(Collectors.groupingBy(GradeSourceResult::studentId));
                for (var student : roster) {
                    var dashboard = queries.teacherRow(student, scheme, batch.getOrDefault(student.studentId(), List.of()));
                    writer.row(row(catalog, dashboard));
                }
                if (!roster.hasNext()) break;
                roster = reads.roster(classroom, roster.nextPageable());
            }
            writer.finish();
        }
        var metadata = new ExportAudit("CLASS", scheme.revisionId(), format.name(), roster.getTotalElements(), catalog.size());
        audit.recordGradebook(classroom, gradebook.getId(), actor, "GRADEBOOK_EXPORTED", null, null, null,
                json.writeValueAsString(metadata), request);
        return file;
    }

    private static List<String> header(List<GradeItem> catalog) {
        var cells = new ArrayList<>(List.of("Student ID", "Username", "Current percentage", "Final percentage"));
        for (var item : catalog) {
            var name = item.title() + " [" + item.sourceType() + " " + item.sourceId() + "]";
            cells.add(name + " earned points");
            cells.add(name + " possible points");
            cells.add(name + " status");
            cells.add(name + " published");
        }
        return cells;
    }

    private static List<Object> row(List<GradeItem> catalog, TeacherGradebookRow dashboard) {
        var cells = new ArrayList<Object>(BASE_COLUMNS + catalog.size() * COLUMNS_PER_ITEM);
        cells.add(dashboard.student().studentId().toString());
        cells.add(dashboard.student().username());
        var overall = dashboard.overall();
        cells.add(overall == null || overall.current() == null ? null : overall.current().percentage());
        cells.add(overall == null || overall.finalGrade() == null ? null : overall.finalGrade().percentage());
        var grades = dashboard.grades().stream().collect(Collectors.toMap(
                grade -> new ItemReference(grade.sourceType(), grade.sourceId()), grade -> grade));
        for (var item : catalog) {
            var grade = grades.get(new ItemReference(item.sourceType(), item.sourceId()));
            cells.add(grade == null ? null : grade.earnedPoints());
            cells.add(grade == null ? null : grade.possiblePoints());
            cells.add(grade == null ? null : grade.status().name());
            cells.add(grade != null && grade.published());
        }
        return cells;
    }
}
