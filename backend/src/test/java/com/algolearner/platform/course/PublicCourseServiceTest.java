package com.algolearner.platform.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.algolearner.platform.common.security.AccountAccess;
import com.algolearner.platform.course.CourseDtos.ProgressStatus;
import com.algolearner.platform.course.CourseDtos.Publication;
import com.algolearner.platform.course.CourseDtos.UnpublishPolicy;
import com.algolearner.platform.course.PublicCourseRepository.CourseRow;
import com.algolearner.platform.course.PublicCourseRepository.EnrollmentRow;
import com.algolearner.platform.course.PublicCourseRepository.LessonRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicCourseServiceTest {
    static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    @Mock PublicCourseRepository repository;
    @Mock AccountAccess accounts;
    @Mock CourseProgressService progress;
    PublicCourseService service;
    final UUID learner = UUID.randomUUID(), courseId = UUID.randomUUID(), lessonId = UUID.randomUUID();

    @BeforeEach void setUp() {
        service = new PublicCourseService(repository, new CourseAccessPolicy(accounts, repository), progress, Clock.fixed(NOW, ZoneOffset.UTC));
        when(accounts.activeRole(learner)).thenReturn("STUDENT");
    }

    @Test void enrollRejectsUnpublishedAndArchivedCoursesForNewLearners() { // FR-PCRS-002 AC-09, FR-PCRS-013 AC-04, FR-PCRS-014 AC-06
        for (Publication status : List.of(Publication.UNPUBLISHED, Publication.ARCHIVED)) {
            when(repository.course(courseId, true)).thenReturn(Optional.of(course(status, UnpublishPolicy.ENROLLED_CONTINUE)));
            when(repository.enrollment(learner, courseId, true)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.enroll(learner, courseId)).hasFieldOrPropertyWithValue("errorCode", "COURSE_NOT_ENROLLABLE");
        }
        verify(repository, never()).insertEnrollment(any(), any(), any());
    }

    @Test void enrollIsIdempotentForExistingEnrollment() {
        when(repository.course(courseId, true)).thenReturn(Optional.of(course(Publication.PUBLISHED, null)));
        when(repository.enrollment(learner, courseId, true)).thenReturn(Optional.of(enrollment(null, null)));
        service.enroll(learner, courseId);
        verify(repository, never()).insertEnrollment(any(), any(), any());
    }

    @Test void adminCannotLearn() { // Admin has no learner role in public courses
        UUID admin = UUID.randomUUID();
        when(accounts.activeRole(admin)).thenReturn("ADMIN");
        assertThatThrownBy(() -> service.enroll(admin, courseId)).hasFieldOrPropertyWithValue("errorCode", "FORBIDDEN_OPERATION");
    }

    @Test void unenrollOnlyBeforeStarting() { // FR-PCRS-002 AC-06..08
        when(repository.course(courseId, false)).thenReturn(Optional.of(course(Publication.PUBLISHED, null)));
        when(repository.enrollment(learner, courseId, true)).thenReturn(Optional.of(enrollment(NOW, null)));
        assertThatThrownBy(() -> service.unenroll(learner, courseId)).hasFieldOrPropertyWithValue("errorCode", "COURSE_ALREADY_STARTED");
        verify(repository, never()).setUnenrolled(any(), any(), any());

        var fresh = enrollment(null, null);
        when(repository.enrollment(learner, courseId, true)).thenReturn(Optional.of(fresh));
        when(repository.hasLearningActivity(fresh.id())).thenReturn(false);
        service.unenroll(learner, courseId);
        verify(repository).setUnenrolled(fresh.id(), NOW, NOW);
        verify(repository).event(fresh.id(), "UNENROLLED", null, NOW);
    }

    @Test void previewLessonIsOpenButOtherLessonsRequireEnrollment() { // FR-PCRS-002 AC-01, FR-PCRS-004 AC-05
        when(repository.course(courseId, false)).thenReturn(Optional.of(course(Publication.PUBLISHED, null)));
        when(repository.enrollment(learner, courseId, true)).thenReturn(Optional.empty());
        when(repository.lesson(courseId, lessonId)).thenReturn(Optional.of(lesson(true)));
        assertThat(service.lesson(learner, courseId, lessonId).preview()).isTrue();
        when(repository.lesson(courseId, lessonId)).thenReturn(Optional.of(lesson(false)));
        assertThatThrownBy(() -> service.lesson(learner, courseId, lessonId)).hasFieldOrPropertyWithValue("errorCode", "COURSE_NOT_ENROLLED");
    }

    @Test void lockAllBlocksContentButEnrolledContinueKeepsIt() { // FR-PCRS-013 AC-05, AC-06
        when(repository.enrollment(learner, courseId, true)).thenReturn(Optional.of(enrollment(NOW, null)));
        when(repository.lesson(courseId, lessonId)).thenReturn(Optional.of(lesson(false)));
        when(repository.course(courseId, false)).thenReturn(Optional.of(course(Publication.UNPUBLISHED, UnpublishPolicy.LOCK_ALL)));
        assertThatThrownBy(() -> service.lesson(learner, courseId, lessonId)).hasFieldOrPropertyWithValue("errorCode", "COURSE_ACCESS_LOCKED");
        when(repository.course(courseId, false)).thenReturn(Optional.of(course(Publication.UNPUBLISHED, UnpublishPolicy.ENROLLED_CONTINUE)));
        when(repository.completedLessonIds(any())).thenReturn(java.util.Set.of());
        assertThat(service.lesson(learner, courseId, lessonId).id()).isEqualTo(lessonId);
    }

    @Test void firstLessonOpenMarksTheCourseStarted() { // FR-PCRS-005 AC-03
        var e = enrollment(null, null);
        var course = course(Publication.PUBLISHED, null);
        when(repository.course(courseId, false)).thenReturn(Optional.of(course));
        when(repository.enrollment(learner, courseId, true)).thenReturn(Optional.of(e));
        when(repository.lesson(courseId, lessonId)).thenReturn(Optional.of(lesson(false)));
        when(repository.completedLessonIds(e.id())).thenReturn(java.util.Set.of());
        service.lesson(learner, courseId, lessonId);
        verify(progress).recompute(eq(course), eq(e), eq(true), eq(false), eq(NOW));
    }

    @Test void draftCourseIsInvisibleToLearners() {
        when(repository.course(courseId, false)).thenReturn(Optional.of(course(Publication.DRAFT, null)));
        assertThatThrownBy(() -> service.detail(learner, courseId)).hasFieldOrPropertyWithValue("errorCode", "COURSE_NOT_FOUND");
    }

    private CourseRow course(Publication status, UnpublishPolicy policy) {
        return new CourseRow(courseId, UUID.randomUUID(), "teacher", "TEACHER", "Course", null, null, null, null, status, policy,
                NOW, null, NOW, NOW, 1, 0);
    }

    private EnrollmentRow enrollment(Instant startedAt, Instant unenrolledAt) {
        return new EnrollmentRow(UUID.randomUUID(), courseId, learner, startedAt == null ? ProgressStatus.NOT_STARTED : ProgressStatus.IN_PROGRESS,
                BigDecimal.ZERO, NOW, startedAt, null, false, unenrolledAt);
    }

    private LessonRow lesson(boolean preview) {
        return new LessonRow(lessonId, courseId, "Lesson", 0, null, null, preview, List.of(CourseDtos.Block.text("x")), null);
    }
}
