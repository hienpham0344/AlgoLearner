package com.algolearner.platform.grading;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.audit.AuditService.RequestContext;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.grading.GradebookDtos.*;
import com.algolearner.platform.notification.NotificationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradebookHistoryService {
    record SchemeApplied(UUID classroomId, UUID gradebookId, UUID actor, SchemeView previous, SchemeView active,
                         RequestContext request) { }
    record GradesAtRevision(Overall teacher, Overall published) { }

    private final GradebookAccess access;
    private final GradebookHistoryRepository history;
    private final GradebookReadRepository reads;
    private final GradebookQueryService queries;
    private final GradeCalculator calculator;
    private final GradebookPublicationRepository publications;
    private final GradebookPublicationService publisher;
    private final AuditService audit;
    private final NotificationService notifications;

    public PageResponse<GradebookHistoryEntry> list(Authentication principal, UUID classroomId, Pageable page) {
        access.requireTeacherRead(principal, classroomId);
        return PageResponse.fromPage(history.list(classroomId, PageRequest.of(page.getPageNumber(), Math.min(100, page.getPageSize()))));
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void schemeApplied(SchemeApplied event) {
        var before = event.previous();
        var after = event.active();
        history.append(event.gradebookId(), null, event.actor(), after.revisionId(), "SCHEME_APPLIED",
                after.reason(), before, after);
        audit.recordGradebook(event.classroomId(), event.gradebookId(), event.actor(), "GRADEBOOK_SCHEME_APPLIED",
                history.encode(before), history.encode(after), after.reason(),
                history.encode(Map.of("schemeRevisionId", after.revisionId())), event.request());

        var published = new java.util.HashSet<>(publications.publishedStudents(event.gradebookId()));
        var catalog = reads.items(event.classroomId());
        var next = PageRequest.of(0, 100);
        for (;;) {
            var roster = reads.roster(event.classroomId(), next);
            var students = roster.stream().map(StudentIdentity::studentId).toList();
            var source = queries.load(event.classroomId(), catalog, students, GradeSourceResult.Audience.TEACHER)
                    .stream().collect(Collectors.groupingBy(GradeSourceResult::studentId));
            var publishedGrades = publisher.calculate(event.classroomId(), after, students.stream().filter(published::contains).toList());
            for (var student : students) {
                var grades = source.getOrDefault(student, List.of());
                var previous = calculator.calculate(before.mode(), before.items(), grades);
                var current = calculator.calculate(after.mode(), after.items(), grades);
                var official = publishedGrades.get(student);
                var old = official == null ? null : publications.current(event.gradebookId(), student).orElseThrow();
                if (!previous.equals(current) || old != null && !old.overall().equals(official.overall())) {
                    history.append(event.gradebookId(), student, event.actor(), after.revisionId(), "SCHEME_RECALCULATED",
                            after.reason(), new GradesAtRevision(previous, old == null ? null : old.overall()),
                            new GradesAtRevision(current, official == null ? null : official.overall()));
                }
                if (old != null) {
                    replacePublished(event.classroomId(), event.gradebookId(), student, old, after.revisionId(), official);
                }
            }
            if (!roster.hasNext()) break;
            next = next.next();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void sourceRecalculated(UUID classroom, UUID gradebook, UUID student, GradebookPublicationRepository.Snapshot previous,
                            UUID scheme, GradeCalculator.Calculation updated) {
        replacePublished(classroom, gradebook, student, previous, scheme, updated);
        var reason = "Published source grade updated";
        history.append(gradebook, student, null, scheme, "SOURCE_RECALCULATED", reason, previous.overall(), updated.overall());
        audit.recordGradebook(classroom, gradebook, null, "GRADEBOOK_SOURCE_RECALCULATED",
                history.encode(previous.overall()), history.encode(updated.overall()), reason,
                history.encode(Map.of("studentId", student, "schemeRevisionId", scheme)), RequestContext.EMPTY);
    }

    private void replacePublished(UUID classroom, UUID gradebook, UUID student, GradebookPublicationRepository.Snapshot previous,
                                  UUID scheme, GradeCalculator.Calculation latest) {
        var result = publications.append(gradebook, student, previous.publicationId(), scheme, latest);
        var before = previous.overall().finalGrade();
        var after = latest.overall().finalGrade();
        if (before != null && after != null && before.percentage().compareTo(after.percentage()) != 0) {
            notifications.notifyGradebookFinalChange(student, classroom, result);
        }
    }
}
