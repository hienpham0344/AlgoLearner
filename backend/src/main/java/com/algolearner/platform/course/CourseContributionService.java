package com.algolearner.platform.course;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.course.CourseDtos.Block;
import com.algolearner.platform.course.CourseDtos.Contribution;
import com.algolearner.platform.course.CourseDtos.ContributionSummary;
import com.algolearner.platform.course.CourseDtos.CourseContent;
import com.algolearner.platform.course.CourseDtos.Lesson;
import com.algolearner.platform.course.CourseDtos.LessonContent;
import com.algolearner.platform.course.CourseDtos.Publication;
import com.algolearner.platform.course.CourseDtos.ReviewStatus;
import com.algolearner.platform.course.PublicCourseRepository.CourseRow;
import com.algolearner.platform.course.PublicCourseRepository.RevisionRow;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher contributions (UC-PUB-11, FR-PCRS-008/011/012). Teachers only edit a revision; the public
 * version changes exclusively when an Admin approves it.
 */
@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseContributionService {
    private final PublicCourseRepository courses;
    private final CourseAccessPolicy policy;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public CourseDtos.Summary create(UUID actor, CourseDtos.Create request) {
        policy.teacher(actor);
        var metadata = CourseContentRules.normalize(new CourseContent(request.title(), request.description(), request.level(),
                request.topic(), request.estimatedMinutes(), List.of(), List.of()), false, courses::publishedProblem);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        courses.insertCourse(id, actor, metadata, now);
        courses.insertRevision(id, actor, metadata, now);
        return courses.course(id, false).orElseThrow().summary();
    }

    public Contribution contribution(UUID actor, UUID courseId) {
        CourseRow course = authored(actor, courseId, false);
        RevisionRow revision = courses.openRevision(courseId).or(() -> courses.latestRevision(courseId))
                .orElseThrow(() -> CourseException.notFound("COURSE_CONTRIBUTION_NOT_FOUND", "Contribution not found"));
        return new Contribution(summary(revision, course), revision.snapshot());
    }

    @Transactional
    public Contribution saveContent(UUID actor, UUID courseId, CourseContent content) {
        CourseRow course = authored(actor, courseId, true);
        RevisionRow revision = editable(course, actor);
        CourseContent normalized = CourseContentRules.normalize(content, false, courses::publishedProblem);
        courses.updateRevisionSnapshot(revision.id(), normalized, revision.status(), clock.instant());
        RevisionRow saved = courses.revision(revision.id()).orElseThrow();
        return new Contribution(summary(saved, course), saved.snapshot());
    }

    /** Appends a lesson to the editable revision (kept for the original MVP endpoint). */
    @Transactional
    public Lesson addLesson(UUID actor, UUID courseId, CourseDtos.UnitRequest request) {
        CourseRow course = authored(actor, courseId, true);
        RevisionRow revision = editable(course, actor);
        List<Block> blocks = request.blocks() != null && !request.blocks().isEmpty() ? request.blocks()
                : request.content() == null ? List.of() : List.of(Block.text(request.content()));
        var lesson = new LessonContent(UUID.randomUUID(), request.title(), request.estimatedMinutes(), request.prerequisiteNote(),
                Boolean.TRUE.equals(request.preview()), blocks);
        var lessons = new ArrayList<>(revision.snapshot().lessons());
        lessons.add(lesson);
        CourseContent current = revision.snapshot();
        CourseContent normalized = CourseContentRules.normalize(new CourseContent(current.title(), current.description(),
                current.level(), current.topic(), current.estimatedMinutes(), lessons, current.quizzes()), false,
                courses::publishedProblem);
        courses.updateRevisionSnapshot(revision.id(), normalized, revision.status(), clock.instant());
        int order = lessons.size() - 1;
        LessonContent saved = normalized.lessons().get(order);
        return new Lesson(saved.id(), courseId, saved.title(), order, saved.estimatedMinutes(), saved.prerequisiteNote(),
                saved.preview(), saved.blocks(), false);
    }

    @Transactional
    public ContributionSummary submit(UUID actor, UUID courseId) {
        CourseRow course = authored(actor, courseId, true);
        RevisionRow revision = courses.openRevision(courseId)
                .orElseThrow(() -> CourseException.conflict("COURSE_STATE_CONFLICT", "There is no draft revision to submit"));
        if (revision.status() == ReviewStatus.PENDING_REVIEW) {
            throw CourseException.conflict("COURSE_STATE_CONFLICT", "Revision is already pending review");
        }
        CourseContentRules.normalize(revision.snapshot(), true, courses::publishedProblem);
        Instant now = clock.instant();
        courses.submitRevision(revision.id(), now);
        audit.record(actor, "TEACHER", "PUBLIC_COURSE", courseId.toString(), "PUBLIC_COURSE_SUBMITTED",
                revision.status().name(), ReviewStatus.PENDING_REVIEW.name(), null, revision.id().toString());
        return summary(courses.revision(revision.id()).orElseThrow(), course);
    }

    public PageResponse<ContributionSummary> mine(UUID actor, ReviewStatus status, Pageable pageable) {
        policy.teacher(actor);
        CourseAccessPolicy.pageSize(pageable);
        var items = courses.revisions(status, actor, pageable.getPageNumber(), pageable.getPageSize()).stream()
                .map(r -> summary(r, courses.course(r.courseId(), false).orElseThrow())).toList();
        return PageResponse.fromPage(new PageImpl<>(items, pageable, courses.countRevisions(status, actor)));
    }

    /** Open revision, or a new one based on the public version (or on the rejected draft of a never-published course). */
    private RevisionRow editable(CourseRow course, UUID actor) {
        var open = courses.openRevision(course.id());
        if (open.isPresent()) {
            if (open.get().status() == ReviewStatus.PENDING_REVIEW) {
                throw CourseException.conflict("COURSE_REVIEW_PENDING", "Content is frozen while it is pending review");
            }
            return open.get();
        }
        CourseContent base = course.status() == Publication.DRAFT
                ? courses.latestRevision(course.id()).map(RevisionRow::snapshot).orElseGet(() -> courses.liveContent(course))
                : courses.liveContent(course);
        return courses.insertRevision(course.id(), actor, base, clock.instant());
    }

    private CourseRow authored(UUID actor, UUID courseId, boolean lock) {
        policy.teacher(actor);
        CourseRow course = courses.course(courseId, lock).orElseThrow(CourseException::notFound);
        if (!course.authorId().equals(actor)) throw CourseException.forbidden("Only the course author may edit it");
        if (course.status() == Publication.ARCHIVED) throw CourseException.conflict("COURSE_STATE_CONFLICT", "Archived courses cannot be edited");
        return course;
    }

    static ContributionSummary summary(RevisionRow r, CourseRow course) {
        return new ContributionSummary(r.id(), r.courseId(), r.authorId(), r.authorName(), r.revisionNo(), r.snapshot().title(),
                r.status(), r.reason(), course.status(), r.submittedAt(), r.reviewedAt(), r.updatedAt());
    }
}
