package com.algolearner.platform.grading;

import com.algolearner.platform.assignment.AssignmentGradeSource;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType;
import com.algolearner.platform.grading.GradebookDtos.*;
import com.algolearner.platform.quiz.QuizGradeSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class GradebookQueryService {
    private final GradebookAccess access;
    private final GradebookSchemeService schemes;
    private final GradebookReadRepository reads;
    private final AssignmentGradeSource assignments;
    private final QuizGradeSource quizzes;
    private final GradeCalculator calculator;
    private final GradebookPublicationRepository publications;
    record StaleSnapshot(UUID gradebookId, UUID studentId, UUID resultId) { }
    record StudentRead(MyGradebookResponse response, StaleSnapshot staleSnapshot) { }

    public PageResponse<TeacherGradebookRow> roster(Authentication principal, UUID classroomId, Pageable page) {
        access.requireTeacherRead(principal, classroomId);
        var roster = reads.roster(classroomId, limited(page));
        var scheme = schemes.active(schemes.find(classroomId));
        var catalog = reads.items(classroomId);
        var grades = load(classroomId, catalog, roster.stream().map(StudentIdentity::studentId).toList(), GradeSourceResult.Audience.TEACHER)
                .stream().collect(Collectors.groupingBy(GradeSourceResult::studentId));
        return PageResponse.fromPage(roster.map(student -> teacherRow(student, scheme,
                grades.getOrDefault(student.studentId(), List.of()))));
    }

    public TeacherGradebookRow student(Authentication principal, UUID classroomId, UUID studentId) {
        access.requireTeacherRead(principal, classroomId);
        access.requireActiveStudentInClass(classroomId, studentId);
        return teacherRow(reads.student(classroomId, studentId), schemes.active(schemes.find(classroomId)),
                load(classroomId, reads.items(classroomId), List.of(studentId), GradeSourceResult.Audience.TEACHER));
    }

    public ClassGradebookSummary summary(Authentication principal, UUID classroomId) {
        access.requireTeacherRead(principal, classroomId);
        var catalog = reads.items(classroomId);
        var scheme = schemes.active(schemes.find(classroomId));
        long graded = 0;
        long completed = 0;
        long submissions = 0;
        long late = 0;
        long withCurrent = 0;
        var currentSum = BigDecimal.ZERO;
        var next = PageRequest.of(0, 100);
        for (;;) {
            var page = reads.roster(classroomId, next);
            var grades = load(classroomId, catalog, page.stream().map(StudentIdentity::studentId).toList(),
                    GradeSourceResult.Audience.TEACHER);
            for (var grade : grades) {
                if (grade.status() == GradeSourceResult.Status.GRADED) graded++;
                if (grade.progress().completed()) completed++;
                submissions += grade.progress().submissionCount();
                late += grade.progress().lateSubmissionCount();
            }
            for (var studentGrades : grades.stream().collect(Collectors.groupingBy(GradeSourceResult::studentId)).values()) {
                var current = calculator.calculate(scheme.mode(), scheme.items(), studentGrades).current();
                if (current != null) {
                    currentSum = currentSum.add(current.percentage());
                    withCurrent++;
                }
            }
            if (!page.hasNext()) {
                var average = withCurrent == 0 ? null : currentSum.divide(BigDecimal.valueOf(withCurrent), 4, RoundingMode.HALF_UP);
                return new ClassGradebookSummary(page.getTotalElements(), catalog.size(), graded, completed, submissions, late, average);
            }
            next = next.next();
        }
    }

    public PageResponse<TeacherSubmissionDetail> submissions(Authentication principal, UUID classroomId,
                                                             SourceType source, UUID itemId, Pageable page) {
        access.requireTeacherRead(principal, classroomId);
        access.requireItemInClass(classroomId, source, itemId);
        return PageResponse.fromPage(reads.submissions(classroomId, source, itemId, limited(page)));
    }

    public StudentRead me(Authentication principal, UUID classroomId) {
        var student = access.requireStudentRead(principal, classroomId);
        var gradebook = schemes.find(classroomId);
        var catalog = reads.items(classroomId);
        var titles = catalog.stream().collect(Collectors.toMap(
                item -> new ItemReference(item.sourceType(), item.sourceId()), GradeItem::title));
        var grades = load(classroomId, catalog, List.of(student), GradeSourceResult.Audience.STUDENT);
        var snapshot = gradebook.isShowOverall() ? publications.current(gradebook.getId(), student).orElse(null) : null;
        var overall = snapshot == null ? null : snapshot.overall();
        StaleSnapshot stale = null;
        if (snapshot != null && overall.current() != null) {
            var visible = grades.stream().filter(grade -> grade.published() && grade.earnedPoints() != null)
                    .map(grade -> new ItemReference(grade.sourceType(), grade.sourceId())).collect(Collectors.toSet());
            if (snapshot.contributors() == null || !visible.containsAll(snapshot.contributors())) {
                stale = new StaleSnapshot(gradebook.getId(), student, snapshot.id());
                overall = null;
            }
        }
        if (overall != null && overall.current() == null && overall.finalGrade() == null) overall = null;
        var response = new MyGradebookResponse(grades.stream().map(grade -> new StudentGrade(grade.sourceType(), grade.sourceId(),
                titles.get(new ItemReference(grade.sourceType(), grade.sourceId())), grade.earnedPoints(), grade.possiblePoints()))
                .toList(), overall, stale == null ? null : OverallStatus.UPDATING);
        return new StudentRead(response, stale);
    }

    List<GradeSourceResult> load(UUID classroomId, List<GradeItem> catalog, List<UUID> students, GradeSourceResult.Audience audience) {
        var itemIds = catalog.stream().collect(Collectors.groupingBy(GradeItem::sourceType,
                Collectors.mapping(GradeItem::sourceId, Collectors.toList())));
        var grades = new ArrayList<>(assignments.load(classroomId,
                itemIds.getOrDefault(SourceType.ASSIGNMENT, List.of()), students, audience));
        grades.addAll(quizzes.load(classroomId, itemIds.getOrDefault(SourceType.QUIZ, List.of()), students, audience));
        return grades;
    }

    TeacherGradebookRow teacherRow(StudentIdentity student, SchemeView scheme, List<GradeSourceResult> grades) {
        var overall = scheme.mode() == null ? null : calculator.calculate(scheme.mode(), scheme.items(), grades);
        return new TeacherGradebookRow(student, grades.stream().map(grade -> new TeacherGradeCell(grade.sourceType(),
                grade.sourceId(), grade.earnedPoints(), grade.possiblePoints(), grade.status(), grade.published(),
                grade.rejudgePending(), grade.provenance(), grade.progress())).toList(), overall);
    }

    private static Pageable limited(Pageable page) {
        return PageRequest.of(page.getPageNumber(), Math.min(100, page.getPageSize()));
    }
}
