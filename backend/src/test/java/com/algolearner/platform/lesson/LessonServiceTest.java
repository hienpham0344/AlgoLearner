package com.algolearner.platform.lesson;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.assignment.AssignmentEntity;
import com.algolearner.platform.assignment.AssignmentRepository;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.ClassroomStatus;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.common.exception.ResourceNotFoundException;
import com.algolearner.platform.storage.FileKind;
import com.algolearner.platform.storage.FileMetadata;
import com.algolearner.platform.storage.FilePurpose;
import com.algolearner.platform.storage.FileState;
import com.algolearner.platform.storage.ManagedFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    private LessonService service;
    private final com.algolearner.platform.classroom.ClassroomService classroomService = mock(com.algolearner.platform.classroom.ClassroomService.class);
    private final LessonProgressService progressService = mock(LessonProgressService.class);
    private final ManagedFileService managedFiles = mock(ManagedFileService.class);

    private AccountEntity teacher;
    private AccountEntity student;
    private ClassroomEntity classroom;
    private LessonEntity lesson;

    @BeforeEach
    void setUp() {
        service = new LessonService(lessons, materials, linkedItems, completions, classrooms, memberships, accounts, assignments, clock, classroomService, List.of(), progressService, managedFiles);

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
        when(classroomService.lockReadable(classroom.getId())).thenReturn(classroom);
        when(classroomService.lockWritable(classroom.getId())).thenReturn(classroom);

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
        assertThat(restored.status()).isEqualTo(LessonStatus.DRAFT);
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
    void linkAssignmentRejectsItemFromAnotherClassroom() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));

        var foreignAssignment = new AssignmentEntity();
        var otherClassroom = new ClassroomEntity();
        otherClassroom.setId(UUID.randomUUID());
        foreignAssignment.setId(UUID.randomUUID());
        foreignAssignment.setClassroom(otherClassroom);
        when(assignments.findByIdAndClassroomId(foreignAssignment.getId(), classroom.getId()))
                .thenReturn(Optional.empty());

        var request = new LessonDtos.LinkItemRequest(LinkedItemType.ASSIGNMENT, foreignAssignment.getId());
        assertThatThrownBy(() -> service.linkItem(teacher.getId(), classroom.getId(), lesson.getId(), request))
                .isInstanceOf(ApiException.class);
        verify(linkedItems, never()).save(any());
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

    @ParameterizedTest
    @EnumSource(value = CompletionMode.class, names = {"AUTO_ITEMS", "NONE"})
    void completeLesson_RejectsManualCompletionOutsideManualMode(CompletionMode mode) {
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setCompletionMode(mode);
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.completeLesson(student.getId(), classroom.getId(), lesson.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());

        verify(completions, never()).save(any());
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
    void addMaterialRejectsUnmanagedUrl() {
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        var req = new LessonDtos.AddMaterialRequest("slides.pdf", "https://storage.test/slides.pdf", MaterialFileType.PDF, 1024L);
        assertThatThrownBy(() -> service.addMaterial(teacher.getId(), classroom.getId(), lesson.getId(), req))
                .isInstanceOf(ApiException.class);
        verify(materials, never()).save(any());
    }

    @Test
    void unlinkItem_Success() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
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
    void unlinkItem_RejectsLessonFromAnotherClassroomWithoutDeletingItsLink() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        var foreignLessonId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var foreignLink = LessonLinkedItemEntity.builder()
                .id(UUID.randomUUID())
                .itemId(itemId)
                .itemType(LinkedItemType.ASSIGNMENT)
                .build();
        when(lessons.findByIdAndClassroomId(foreignLessonId, classroom.getId())).thenReturn(Optional.empty());
        when(linkedItems.findByLessonIdAndItemId(foreignLessonId, itemId)).thenReturn(Optional.of(foreignLink));

        assertThatThrownBy(() -> service.unlinkItem(teacher.getId(), classroom.getId(), foreignLessonId, itemId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LESSON_NOT_FOUND.getCode());

        verify(linkedItems, never()).delete(any());
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

    @Test
    void classroomScopedMutationsAcquireWritableLock() {
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        when(lessons.save(any(LessonEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(materials.save(any(LessonMaterialEntity.class))).thenAnswer(i -> i.getArgument(0));
        var fileId = UUID.randomUUID();
        when(managedFiles.requireReady(teacher.getId(), classroom.getId(), fileId, FilePurpose.LESSON_MATERIAL))
                .thenReturn(new FileMetadata(fileId, "notes.pdf", "application/pdf", FileKind.PDF, 128L,
                        FilePurpose.LESSON_MATERIAL, FileState.READY, "a".repeat(64), clock.instant()));
        var materialId = UUID.randomUUID();
        var material = LessonMaterialEntity.builder().id(materialId).lesson(lesson).fileName("notes.pdf")
                .fileUrl("/api/v1/files/" + fileId + "/content").fileType(MaterialFileType.PDF).fileSize(128L).build();
        when(materials.findByIdAndLessonId(materialId, lesson.getId())).thenReturn(Optional.of(material));

        service.create(teacher.getId(), classroom.getId(),
                new LessonDtos.CreateLessonRequest("New Lesson", "Body", null, "[]", CompletionMode.MANUAL));
        service.update(teacher.getId(), classroom.getId(), lesson.getId(),
                new LessonDtos.UpdateLessonRequest("Updated", "Body", null, "[]", CompletionMode.MANUAL));
        service.addMaterial(teacher.getId(), classroom.getId(), lesson.getId(),
                new LessonDtos.AddMaterialRequest(fileId, "notes.pdf", null, MaterialFileType.PDF, 128L));
        service.removeMaterial(teacher.getId(), classroom.getId(), lesson.getId(), materialId);
        var rule = new LessonCompletionRule(LessonCompletionRule.Operator.ALL,
                List.of(new LessonCompletionRule.Condition(LinkedItemType.ASSIGNMENT, UUID.randomUUID(),
                        LessonCompletionRule.Requirement.SUBMITTED, null)));
        service.configureCompletionRule(teacher.getId(), classroom.getId(), lesson.getId(), rule);

        verify(classroomService, times(5)).lockWritable(classroom.getId());
        verify(classroomService, never()).lockReadable(classroom.getId());
    }

    @Test
    void removeMaterial_DeletesMaterialAndGuardsStateAndOwnership() {
        var materialId = UUID.randomUUID();
        var material = LessonMaterialEntity.builder()
                .id(materialId)
                .lesson(lesson)
                .fileName("slides.pdf")
                .fileUrl("/api/v1/files/" + UUID.randomUUID() + "/content")
                .fileType(MaterialFileType.PDF)
                .fileSize(512L)
                .build();
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        when(materials.findByIdAndLessonId(materialId, lesson.getId())).thenReturn(Optional.of(material));

        service.removeMaterial(teacher.getId(), classroom.getId(), lesson.getId(), materialId);
        verify(materials).delete(material);

        var foreignMaterialId = UUID.randomUUID();
        when(materials.findByIdAndLessonId(foreignMaterialId, lesson.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.removeMaterial(teacher.getId(), classroom.getId(), lesson.getId(), foreignMaterialId))
                .isInstanceOf(ResourceNotFoundException.class);

        lesson.setStatus(LessonStatus.ARCHIVED);
        assertThatThrownBy(() -> service.removeMaterial(teacher.getId(), classroom.getId(), lesson.getId(), materialId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());
    }

    @Test
    void get_PopulatesEnrichedDetailFieldsAndLinkedItemMetadata() {
        var linkedAssignmentId = UUID.randomUUID();
        lesson.setStatus(LessonStatus.ARCHIVED);
        lesson.setArchivedFromStatus(LessonStatus.PUBLISHED);
        lesson.setCompletionMode(CompletionMode.AUTO_ITEMS);
        lesson.setRuleVersion(2L);

        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        when(completions.existsByLessonId(lesson.getId())).thenReturn(true);
        when(linkedItems.countByLessonId(lesson.getId())).thenReturn(1L);
        var linked = LessonLinkedItemEntity.builder()
                .id(UUID.randomUUID())
                .lesson(lesson)
                .itemType(LinkedItemType.ASSIGNMENT)
                .itemId(linkedAssignmentId)
                .displayOrder(0)
                .build();
        when(linkedItems.findByLessonIdOrderByDisplayOrderAsc(lesson.getId())).thenReturn(List.of(linked));
        when(progressService.rule(lesson.getId())).thenReturn(new LessonDtos.CompletionRuleView(2L,
                new LessonCompletionRule(LessonCompletionRule.Operator.ALL, List.of(
                        new LessonCompletionRule.Condition(LinkedItemType.ASSIGNMENT, linkedAssignmentId,
                                LessonCompletionRule.Requirement.SUBMITTED, null),
                        new LessonCompletionRule.Condition(LinkedItemType.ASSIGNMENT, linkedAssignmentId,
                                LessonCompletionRule.Requirement.SCORE_AT_LEAST, java.math.BigDecimal.valueOf(70))
                ))));

        var provider = mock(LessonProgressProvider.class);
        when(provider.itemType()).thenReturn(LinkedItemType.ASSIGNMENT);
        when(provider.classroomItems(classroom.getId())).thenReturn(List.of(
                new LessonProgressProvider.ClassroomItemSummary(linkedAssignmentId, "Week 1 Homework", true)
        ));
        var serviceWithProviders = new LessonService(lessons, materials, linkedItems, completions, classrooms, memberships,
                accounts, assignments, clock, classroomService, List.of(provider), progressService, managedFiles);

        var detail = serviceWithProviders.get(teacher.getId(), classroom.getId(), lesson.getId());
        assertThat(detail.previousStatus()).isEqualTo(LessonStatus.PUBLISHED);
        assertThat(detail.canDelete()).isFalse();
        assertThat(detail.requiredItemIds()).containsExactly(linkedAssignmentId);
        assertThat(detail.linkedItems()).hasSize(1);
        assertThat(detail.linkedItems().getFirst().title()).isEqualTo("Week 1 Homework");
        assertThat(detail.linkedItems().getFirst().available()).isTrue();
    }

    @Test
    void capabilitiesAndActivities_ReturnOwnerMetadataAndRejectNonOwner() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(managedFiles.capabilities()).thenReturn(new ManagedFileService.UploadCapabilities(
                true, 10_485_760L, List.of("pdf", "png")));

        var assignmentId = UUID.randomUUID();
        var quizId = UUID.randomUUID();
        var otherLesson = LessonEntity.builder().id(UUID.randomUUID()).classroom(classroom).title("Other").build();
        var linkedQuiz = LessonLinkedItemEntity.builder()
                .id(UUID.randomUUID())
                .lesson(otherLesson)
                .itemType(LinkedItemType.QUIZ)
                .itemId(quizId)
                .displayOrder(0)
                .build();
        when(linkedItems.findByLessonClassroomId(classroom.getId())).thenReturn(List.of(linkedQuiz));

        var assignmentProvider = mock(LessonProgressProvider.class);
        when(assignmentProvider.itemType()).thenReturn(LinkedItemType.ASSIGNMENT);
        when(assignmentProvider.classroomItems(classroom.getId())).thenReturn(List.of(
                new LessonProgressProvider.ClassroomItemSummary(assignmentId, "Lab 1", true)));
        var quizProvider = mock(LessonProgressProvider.class);
        when(quizProvider.itemType()).thenReturn(LinkedItemType.QUIZ);
        when(quizProvider.classroomItems(classroom.getId())).thenReturn(List.of(
                new LessonProgressProvider.ClassroomItemSummary(quizId, "Quiz 1", false)));

        var serviceWithProviders = new LessonService(lessons, materials, linkedItems, completions, classrooms, memberships,
                accounts, assignments, clock, classroomService, List.of(assignmentProvider, quizProvider), progressService, managedFiles);

        var caps = serviceWithProviders.capabilities(teacher.getId(), classroom.getId());
        assertThat(caps.uploadsAvailable()).isTrue();
        assertThat(caps.activityLinkingAvailable()).isTrue();
        assertThat(caps.maxFileSizeBytes()).isEqualTo(10_485_760L);
        assertThat(caps.allowedExtensions()).containsExactly("pdf", "png");

        var activities = serviceWithProviders.activities(teacher.getId(), classroom.getId());
        assertThat(activities).containsExactly(
                new LessonDtos.ActivityOptionView(assignmentId, LinkedItemType.ASSIGNMENT, "Lab 1", null, true),
                new LessonDtos.ActivityOptionView(quizId, LinkedItemType.QUIZ, "Quiz 1", otherLesson.getId(), false)
        );

        assertThatThrownBy(() -> serviceWithProviders.capabilities(student.getId(), classroom.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());
        assertThatThrownBy(() -> serviceWithProviders.activities(student.getId(), classroom.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());
    }

    @Test
    void studentDetail_GatesOwnerMetadataAndFiltersUnavailableRequiredItemIds() {
        var visibleItemId = UUID.randomUUID();
        var hiddenItemId = UUID.randomUUID();
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setArchivedFromStatus(LessonStatus.DRAFT);
        lesson.setCompletionMode(CompletionMode.AUTO_ITEMS);
        lesson.setRuleVersion(1L);

        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(lessons.findByIdAndClassroomId(lesson.getId(), classroom.getId())).thenReturn(Optional.of(lesson));
        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        var hiddenLink = LessonLinkedItemEntity.builder()
                .id(UUID.randomUUID())
                .lesson(lesson)
                .itemType(LinkedItemType.ASSIGNMENT)
                .itemId(hiddenItemId)
                .displayOrder(0)
                .build();
        when(linkedItems.findByLessonIdOrderByDisplayOrderAsc(lesson.getId())).thenReturn(List.of(hiddenLink));
        when(progressService.rule(lesson.getId())).thenReturn(new LessonDtos.CompletionRuleView(1L,
                new LessonCompletionRule(LessonCompletionRule.Operator.ALL, List.of(
                        new LessonCompletionRule.Condition(LinkedItemType.ASSIGNMENT, hiddenItemId,
                                LessonCompletionRule.Requirement.SUBMITTED, null)
                ))));

        var provider = mock(LessonProgressProvider.class);
        when(provider.itemType()).thenReturn(LinkedItemType.ASSIGNMENT);
        when(provider.availableToStudent(hiddenItemId, classroom.getId(), student.getId())).thenReturn(false);
        when(provider.availableToStudent(visibleItemId, classroom.getId(), student.getId())).thenReturn(true);
        when(provider.classroomItems(classroom.getId())).thenReturn(List.of(
                new LessonProgressProvider.ClassroomItemSummary(hiddenItemId, "Draft Lab", false),
                new LessonProgressProvider.ClassroomItemSummary(visibleItemId, "Published Lab", true)
        ));

        var serviceWithProviders = new LessonService(lessons, materials, linkedItems, completions, classrooms, memberships,
                accounts, assignments, clock, classroomService, List.of(provider), progressService, managedFiles);

        var studentHiddenOnly = serviceWithProviders.get(student.getId(), classroom.getId(), lesson.getId());
        assertThat(studentHiddenOnly.linkedItems()).isEmpty();
        assertThat(studentHiddenOnly.requiredItemIds()).isEmpty();
        assertThat(studentHiddenOnly.canDelete()).isFalse();
        assertThat(studentHiddenOnly.previousStatus()).isNull();
        verify(completions, never()).existsByLessonId(lesson.getId());
        verify(linkedItems, never()).countByLessonId(lesson.getId());
        verify(provider, never()).classroomItems(classroom.getId());

        var visibleLink = LessonLinkedItemEntity.builder()
                .id(UUID.randomUUID())
                .lesson(lesson)
                .itemType(LinkedItemType.ASSIGNMENT)
                .itemId(visibleItemId)
                .displayOrder(1)
                .build();
        when(linkedItems.findByLessonIdOrderByDisplayOrderAsc(lesson.getId())).thenReturn(List.of(hiddenLink, visibleLink));
        when(progressService.rule(lesson.getId())).thenReturn(new LessonDtos.CompletionRuleView(1L,
                new LessonCompletionRule(LessonCompletionRule.Operator.ALL, List.of(
                        new LessonCompletionRule.Condition(LinkedItemType.ASSIGNMENT, hiddenItemId,
                                LessonCompletionRule.Requirement.SUBMITTED, null),
                        new LessonCompletionRule.Condition(LinkedItemType.ASSIGNMENT, visibleItemId,
                                LessonCompletionRule.Requirement.SUBMITTED, null)
                ))));

        var studentMixed = serviceWithProviders.get(student.getId(), classroom.getId(), lesson.getId());
        assertThat(studentMixed.linkedItems()).extracting(LessonDtos.LinkedItemView::itemId).containsExactly(visibleItemId);
        assertThat(studentMixed.requiredItemIds()).containsExactly(visibleItemId);
        assertThat(studentMixed.canDelete()).isFalse();
        assertThat(studentMixed.previousStatus()).isNull();
    }

    @Test
    void nonOwnerTeacherGetsForbiddenOnRemoveMaterialCapabilitiesAndActivities() {
        var otherTeacherId = UUID.randomUUID();
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThatThrownBy(() -> service.removeMaterial(otherTeacherId, classroom.getId(), lesson.getId(), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());
        assertThatThrownBy(() -> service.capabilities(otherTeacherId, classroom.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());
        assertThatThrownBy(() -> service.activities(otherTeacherId, classroom.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());
    }
}
