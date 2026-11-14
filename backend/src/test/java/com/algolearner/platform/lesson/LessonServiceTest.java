package com.algolearner.platform.lesson;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.ClassroomStatus;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LessonServiceTest {

    private final LessonRepository lessons = mock(LessonRepository.class);
    private final LessonMaterialRepository materials = mock(LessonMaterialRepository.class);
    private final LessonLinkedItemRepository linkedItems = mock(LessonLinkedItemRepository.class);
    private final LessonCompletionRepository completions = mock(LessonCompletionRepository.class);
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final ClassroomMembershipRepository memberships = mock(ClassroomMembershipRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    private LessonService service;

    private AccountEntity teacher;
    private AccountEntity student;
    private ClassroomEntity classroom;
    private LessonEntity lesson;

    @BeforeEach
    void setUp() {
        service = new LessonService(lessons, materials, linkedItems, completions, classrooms, memberships, accounts, clock);

        teacher = new AccountEntity();
        teacher.setId(UUID.randomUUID());
        teacher.setUsername("teacher");
        teacher.setFullName("Teacher");
        teacher.setRole(AccountRole.TEACHER);
        teacher.setStatus(AccountStatus.ACTIVE);

        student = new AccountEntity();
        student.setId(UUID.randomUUID());
        student.setUsername("student");
        student.setFullName("Student");
        student.setRole(AccountRole.STUDENT);
        student.setStatus(AccountStatus.ACTIVE);

        classroom = new ClassroomEntity();
        classroom.setId(UUID.randomUUID());
        classroom.setName("Algorithms");
        classroom.setOwner(teacher);
        classroom.setStatus(ClassroomStatus.ACTIVE);

        lesson = LessonEntity.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .title("Introduction to Graphs")
                .status(LessonStatus.DRAFT)
                .completionMode(CompletionMode.MANUAL)
                .displayOrder(0)
                .build();
    }

    @Test
    void publishAndUnpublishLesson_Success() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        when(lessons.save(any(LessonEntity.class))).thenAnswer(i -> i.getArgument(0));

        var published = service.publish(teacher.getId(), classroom.getId(), lesson.getId());
        assertThat(published.status()).isEqualTo(LessonStatus.PUBLISHED);

        var unpublished = service.unpublish(teacher.getId(), classroom.getId(), lesson.getId());
        assertThat(unpublished.status()).isEqualTo(LessonStatus.DRAFT);
    }

    @Test
    void hardDeleteLesson_BlockedWhenCompletionsExist_ThrowsException() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        when(completions.existsByLessonId(lesson.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(teacher.getId(), classroom.getId(), lesson.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_HARD_DELETE_LESSON.getCode());

        verify(lessons, never()).delete(any());
    }

    @Test
    void hardDeleteLesson_SuccessWhenNoData() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        when(completions.existsByLessonId(lesson.getId())).thenReturn(false);
        when(linkedItems.countByLessonId(lesson.getId())).thenReturn(0L);

        service.delete(teacher.getId(), classroom.getId(), lesson.getId());
        verify(lessons).delete(lesson);
    }

    @Test
    void archiveAndRestoreLesson_Success() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        when(lessons.save(any(LessonEntity.class))).thenAnswer(i -> i.getArgument(0));

        var archived = service.archive(teacher.getId(), classroom.getId(), lesson.getId());
        assertThat(archived.status()).isEqualTo(LessonStatus.ARCHIVED);

        var restored = service.restore(teacher.getId(), classroom.getId(), lesson.getId());
        assertThat(restored.status()).isEqualTo(LessonStatus.PUBLISHED);
    }

    @Test
    void linkQuiz_ThrowsWhenQuizAlreadyLinked() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));

        var quizId = UUID.randomUUID();
        when(linkedItems.existsByItemTypeAndItemId(LinkedItemType.QUIZ, quizId)).thenReturn(true);

        var req = new LessonDtos.LinkItemRequest(LinkedItemType.QUIZ, quizId);
        assertThatThrownBy(() -> service.linkItem(teacher.getId(), classroom.getId(), lesson.getId(), req))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUIZ_ALREADY_LINKED.getCode());
    }

    @Test
    void completeLesson_ManualMode_Success() {
        lesson.setStatus(LessonStatus.PUBLISHED);
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));

        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
        when(completions.findByLessonIdAndStudentId(lesson.getId(), student.getId()))
                .thenReturn(Optional.empty());

        service.completeLesson(student.getId(), classroom.getId(), lesson.getId());

        verify(completions).save(argThat(c ->
                c.isCompleted() && c.getCompletedAt().equals(clock.instant())
        ));
    }

    @Test
    void updateLesson_ModifiesContentAndTitle() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        when(lessons.save(any(LessonEntity.class))).thenAnswer(i -> i.getArgument(0));

        var req = new LessonDtos.UpdateLessonRequest(
                "Updated Title",
                "# Updated Content",
                "// code example",
                "[\"https://youtube.com/watch?v=123\"]",
                CompletionMode.AUTO_ITEMS
        );

        var updated = service.update(teacher.getId(), classroom.getId(), lesson.getId(), req);
        assertThat(updated.title()).isEqualTo("Updated Title");
        assertThat(updated.content()).isEqualTo("# Updated Content");
        assertThat(updated.completionMode()).isEqualTo(CompletionMode.AUTO_ITEMS);
    }

    @Test
    void addMaterial_SavesMaterial() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        when(materials.save(any(LessonMaterialEntity.class))).thenAnswer(i -> i.getArgument(0));

        var req = new LessonDtos.AddMaterialRequest("slides.pdf", "https://storage.test/slides.pdf", MaterialFileType.PDF, 1024L);
        var res = service.addMaterial(teacher.getId(), classroom.getId(), lesson.getId(), req);

        assertThat(res.fileName()).isEqualTo("slides.pdf");
        assertThat(res.fileType()).isEqualTo(MaterialFileType.PDF);
    }

    @Test
    void unlinkItem_Success() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        var itemId = UUID.randomUUID();
        var linked = LessonLinkedItemEntity.builder()
                .id(UUID.randomUUID())
                .lesson(lesson)
                .itemId(itemId)
                .itemType(LinkedItemType.ASSIGNMENT)
                .build();

        when(linkedItems.findByLessonIdAndItemId(lesson.getId(), itemId)).thenReturn(Optional.of(linked));

        service.unlinkItem(teacher.getId(), classroom.getId(), lesson.getId(), itemId);

        verify(linkedItems).delete(linked);
    }

    @Test
    void list_ReturnsLessonsForOwnerAndStudent() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByClassroomIdOrderByDisplayOrderAsc(classroom.getId()))
                .thenReturn(List.of(lesson));

        var ownerList = service.list(teacher.getId(), classroom.getId());
        assertThat(ownerList).hasSize(1);

        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));
        when(lessons.findByClassroomIdAndStatusInOrderByDisplayOrderAsc(eq(classroom.getId()), anyList()))
                .thenReturn(List.of(lesson));

        var studentList = service.list(student.getId(), classroom.getId());
        assertThat(studentList).hasSize(1);
    }

    @Test
    void get_ReturnsDetailForOwnerAndStudent() {
        lesson.setStatus(LessonStatus.PUBLISHED);
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId()))
                .thenReturn(Optional.of(lesson));

        var ownerDetail = service.get(teacher.getId(), classroom.getId(), lesson.getId());
        assertThat(ownerDetail).isNotNull();
        assertThat(ownerDetail.title()).isEqualTo(lesson.getTitle());

        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        var studentDetail = service.get(student.getId(), classroom.getId(), lesson.getId());
        assertThat(studentDetail).isNotNull();
        assertThat(studentDetail.title()).isEqualTo(lesson.getTitle());
    }
}
