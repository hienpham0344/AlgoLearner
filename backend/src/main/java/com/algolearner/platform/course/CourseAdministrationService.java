package com.algolearner.platform.course;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.course.CourseDtos.AdminDetail;
import com.algolearner.platform.course.CourseDtos.Contribution;
import com.algolearner.platform.course.CourseDtos.ContributionSummary;
import com.algolearner.platform.course.CourseDtos.CourseContent;
import com.algolearner.platform.course.CourseDtos.Publication;
import com.algolearner.platform.course.CourseDtos.PublicationChange;
import com.algolearner.platform.course.CourseDtos.Review;
import com.algolearner.platform.course.CourseDtos.ReviewDecision;
import com.algolearner.platform.course.CourseDtos.ReviewStatus;
import com.algolearner.platform.course.CourseDtos.UnpublishPolicy;
import com.algolearner.platform.course.PublicCourseRepository.CatalogFilter;
import com.algolearner.platform.course.PublicCourseRepository.CourseRow;
import com.algolearner.platform.course.PublicCourseRepository.RevisionRow;
import com.algolearner.platform.notification.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin management of public courses (UC-ADM-04/06, FR-PCRS-007/012/013/014/015). Every state change is audited. */
@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseAdministrationService {
    private final PublicCourseRepository courses;
    private final CourseAccessPolicy policy;
    private final CourseProgressService progress;
    private final AuditService audit;
    private final NotificationService notifications;
    private final AccountRepository accounts;
    private final Clock clock;

    public PageResponse<CourseDtos.Summary> list(UUID actor, Publication status, String query, Pageable pageable) {
        policy.admin(actor);
        CourseAccessPolicy.pageSize(pageable);
        var filter = new CatalogFilter(CourseContentRules.blankToNull(query), null, null,
                status == null ? EnumSet.allOf(Publication.class) : EnumSet.of(status), null);
        var items = courses.courses(filter, pageable.getPageNumber(), pageable.getPageSize()).stream().map(CourseRow::summary).toList();
        return PageResponse.fromPage(new PageImpl<>(items, pageable, courses.countCourses(filter)));
    }

    public AdminDetail detail(UUID actor, UUID courseId) {
        policy.admin(actor);
        CourseRow course = courses.course(courseId, false).orElseThrow(CourseException::notFound);
        return adminDetail(course);
    }

    public PageResponse<ContributionSummary> queue(UUID actor, ReviewStatus status, Pageable pageable) {
        policy.admin(actor);
        CourseAccessPolicy.pageSize(pageable);
        var items = courses.revisions(status, null, pageable.getPageNumber(), pageable.getPageSize()).stream()
                .map(r -> CourseContributionService.summary(r, courses.course(r.courseId(), false).orElseThrow())).toList();
        return PageResponse.fromPage(new PageImpl<>(items, pageable, courses.countRevisions(status, null)));
    }

    public Contribution revision(UUID actor, UUID revisionId) {
        policy.admin(actor);
        RevisionRow revision = courses.revision(revisionId)
                .orElseThrow(() -> CourseException.notFound("COURSE_CONTRIBUTION_NOT_FOUND", "Contribution not found"));
        CourseRow course = courses.course(revision.courseId(), false).orElseThrow(CourseException::notFound);
        return new Contribution(CourseContributionService.summary(revision, course), revision.snapshot());
    }

    @Transactional
    public ContributionSummary review(UUID actor, UUID courseId, Review request) {
        policy.admin(actor);
        CourseRow course = courses.course(courseId, true).orElseThrow(CourseException::notFound);
        RevisionRow revision = courses.openRevision(courseId).filter(r -> r.status() == ReviewStatus.PENDING_REVIEW)
                .orElseThrow(() -> CourseException.conflict("COURSE_STATE_CONFLICT", "Course is not pending review"));
        ReviewDecision decision = request.resolved();
        if (decision == null) throw CourseException.invalid("Review decision is required");
        String reason = CourseContentRules.blankToNull(request.reason());
        if (decision != ReviewDecision.APPROVE && reason == null) {
            throw CourseException.invalid("A reason is required for Changes Requested or Rejected");
        }
        Instant now = clock.instant();
        ReviewStatus outcome = switch (decision) {
            case APPROVE -> ReviewStatus.APPROVED;
            case CHANGES_REQUESTED -> ReviewStatus.CHANGES_REQUESTED;
            case REJECT -> ReviewStatus.REJECTED;
        };
        if (decision == ReviewDecision.APPROVE) {
            CourseContent content = CourseContentRules.normalize(revision.snapshot(), true, courses::publishedProblem);
            boolean publish = request.publish() != null ? request.publish() : course.status() == Publication.DRAFT;
            apply(course, content, now);
            if (publish && course.status() != Publication.PUBLISHED && course.status() != Publication.ARCHIVED) {
                courses.setPublication(courseId, Publication.PUBLISHED, null, now);
                audit.record(actor, "ADMIN", "PUBLIC_COURSE", courseId.toString(), "PUBLIC_COURSE_PUBLICATION_CHANGED",
                        course.status().name(), Publication.PUBLISHED.name(), reason, revision.id().toString());
            }
        }
        courses.reviewRevision(revision.id(), outcome, reason, actor, now);
        audit.record(actor, "ADMIN", "PUBLIC_COURSE", courseId.toString(), "PUBLIC_COURSE_REVIEWED",
                ReviewStatus.PENDING_REVIEW.name(), outcome.name(), reason, revision.id().toString());
        notifyAuthor(revision, outcome, reason);
        CourseRow updated = courses.course(courseId, false).orElseThrow();
        return CourseContributionService.summary(courses.revision(revision.id()).orElseThrow(), updated);
    }

    /** Admin-authored course; Admin content is applied directly (Admin is the approver). */
    @Transactional
    public AdminDetail create(UUID actor, CourseContent request) {
        policy.admin(actor);
        CourseContent content = CourseContentRules.normalize(request, false, courses::publishedProblem);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        courses.insertCourse(id, actor, content, now);
        courses.applyContent(id, content, now);
        audit.record(actor, "ADMIN", "PUBLIC_COURSE", id.toString(), "PUBLIC_COURSE_CREATED", null, Publication.DRAFT.name(), null, null);
        return adminDetail(courses.course(id, false).orElseThrow());
    }

    @Transactional
    public AdminDetail updateContent(UUID actor, UUID courseId, CourseContent request) {
        policy.admin(actor);
        CourseRow course = courses.course(courseId, true).orElseThrow(CourseException::notFound);
        if (course.status() == Publication.ARCHIVED) throw CourseException.conflict("COURSE_STATE_CONFLICT", "Archived courses cannot be edited");
        CourseContent content = CourseContentRules.normalize(request, course.status() != Publication.DRAFT, courses::publishedProblem);
        apply(course, content, clock.instant());
        audit.record(actor, "ADMIN", "PUBLIC_COURSE", courseId.toString(), "PUBLIC_COURSE_CONTENT_UPDATED",
                course.status().name(), course.status().name(), null, null);
        return adminDetail(courses.course(courseId, false).orElseThrow());
    }

    @Transactional
    public CourseDtos.Summary changePublication(UUID actor, UUID courseId, PublicationChange request) {
        policy.admin(actor);
        CourseRow course = courses.course(courseId, true).orElseThrow(CourseException::notFound);
        Publication from = course.status();
        Publication target;
        UnpublishPolicy unpublishPolicy = null;
        switch (request.action()) {
            case PUBLISH -> {
                if (from == Publication.ARCHIVED) throw CourseException.conflict("COURSE_STATE_CONFLICT", "Archived courses cannot be republished");
                if (course.lessonCount() == 0) throw CourseException.unprocessable("COURSE_EMPTY", "Course must contain a lesson");
                target = Publication.PUBLISHED;
            }
            case UNPUBLISH -> {
                if (from != Publication.PUBLISHED && from != Publication.UNPUBLISHED) {
                    throw CourseException.conflict("COURSE_STATE_CONFLICT", "Only published courses can be unpublished");
                }
                if (request.unpublishPolicy() == null) throw CourseException.invalid("Unpublish policy is required");
                target = Publication.UNPUBLISHED;
                unpublishPolicy = request.unpublishPolicy();
            }
            case ARCHIVE -> {
                if (from == Publication.ARCHIVED) throw CourseException.conflict("COURSE_STATE_CONFLICT", "Course is already archived");
                target = Publication.ARCHIVED;
            }
            default -> throw CourseException.invalid("Unsupported publication action");
        }
        courses.setPublication(courseId, target, unpublishPolicy, clock.instant());
        String after = unpublishPolicy == null ? target.name() : target.name() + ":" + unpublishPolicy.name();
        audit.record(actor, "ADMIN", "PUBLIC_COURSE", courseId.toString(), "PUBLIC_COURSE_PUBLICATION_CHANGED",
                from.name(), after, CourseContentRules.blankToNull(request.reason()), null);
        return courses.course(courseId, false).orElseThrow().summary();
    }

    @Transactional
    public void delete(UUID actor, UUID courseId) {
        policy.admin(actor);
        CourseRow course = courses.course(courseId, true).orElseThrow(CourseException::notFound);
        if (courses.enrollmentCount(courseId) > 0) {
            throw CourseException.conflict("COURSE_HAS_LEARNING_DATA", "Courses with enrollments must be archived instead of deleted");
        }
        audit.record(actor, "ADMIN", "PUBLIC_COURSE", courseId.toString(), "PUBLIC_COURSE_DELETED", course.status().name(), null, null, null);
        courses.deleteCourse(courseId);
    }

    private void apply(CourseRow course, CourseContent content, Instant now) {
        CourseContent before = courses.liveContent(course);
        courses.applyContent(course.id(), content, now);
        boolean changed = !before.lessons().equals(content.lessons()) || !before.quizzes().equals(content.quizzes());
        progress.recomputeCourse(courses.course(course.id(), false).orElseThrow(), changed, now);
    }

    private void notifyAuthor(RevisionRow revision, ReviewStatus outcome, String reason) {
        var author = accounts.findById(revision.authorId()).orElse(null);
        if (author == null || author.getRole() == null || !author.getRole().name().equals("TEACHER")) return;
        String title = switch (outcome) {
            case APPROVED -> "Course contribution approved";
            case CHANGES_REQUESTED -> "Changes requested for your course";
            default -> "Course contribution rejected";
        };
        String message = "\"" + revision.snapshot().title() + "\"" + (reason == null ? "" : ": " + reason);
        notifications.notifyStudent(author, "PUBLIC_COURSE_REVIEW", title, message.length() > 1000 ? message.substring(0, 1000) : message,
                "/teacher/courses/" + revision.courseId(), null, null);
    }

    private AdminDetail adminDetail(CourseRow course) {
        var open = courses.openRevision(course.id()).map(r -> CourseContributionService.summary(r, course)).orElse(null);
        return new AdminDetail(course.summary(), courses.liveContent(course), open, courses.enrollmentCount(course.id()));
    }
}
