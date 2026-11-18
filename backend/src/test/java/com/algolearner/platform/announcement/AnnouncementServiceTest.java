package com.algolearner.platform.announcement;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnnouncementServiceTest {

    private final AnnouncementRepository announcements = mock(AnnouncementRepository.class);
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final ClassroomMembershipRepository memberships = mock(ClassroomMembershipRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    private final ClassroomService classroomService = mock(ClassroomService.class);
    private AnnouncementService service;

    private AccountEntity teacher;
    private AccountEntity otherTeacher;
    private AccountEntity student;
    private ClassroomEntity classroom;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(announcements, classrooms, memberships, accounts, notifications, clock, classroomService);

        teacher = new AccountEntity();
        teacher.setId(UUID.randomUUID());
        teacher.setUsername("teacher");
        teacher.setFullName("Teacher User");
        teacher.setEmail("teacher@example.test");
        teacher.setRole(AccountRole.TEACHER);
        teacher.setStatus(AccountStatus.ACTIVE);

        otherTeacher = new AccountEntity();
        otherTeacher.setId(UUID.randomUUID());
        otherTeacher.setUsername("other");
        otherTeacher.setFullName("Other Teacher");
        otherTeacher.setEmail("other@example.test");
        otherTeacher.setRole(AccountRole.TEACHER);
        otherTeacher.setStatus(AccountStatus.ACTIVE);

        student = new AccountEntity();
        student.setId(UUID.randomUUID());
        student.setUsername("student");
        student.setFullName("Student User");
        student.setEmail("student@example.test");
        student.setRole(AccountRole.STUDENT);
        student.setStatus(AccountStatus.ACTIVE);

        classroom = new ClassroomEntity();
        classroom.setId(UUID.randomUUID());
        classroom.setName("Algorithms 101");
        classroom.setOwner(teacher);
        classroom.setStatus(ClassroomStatus.ACTIVE);
        when(classroomService.lockWritable(classroom.getId())).thenReturn(classroom);
        when(accounts.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        when(accounts.findById(otherTeacher.getId())).thenReturn(Optional.of(otherTeacher));
        when(accounts.findById(student.getId())).thenReturn(Optional.of(student));
    }

    @Test
    void inactiveOwnerCannotReadAnnouncements() {
        teacher.setStatus(AccountStatus.SUSPENDED);
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(accounts.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        assertThatThrownBy(() -> service.list(teacher.getId(), classroom.getId(), PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(announcements);
    }

    @Test
    void archivedClassroomRejectsAnnouncementMutation() {
        classroom.setStatus(ClassroomStatus.ARCHIVED);
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        assertThatThrownBy(() -> service.create(teacher.getId(), classroom.getId(),
                new AnnouncementDtos.CreateRequest("Title", "Content"))).isInstanceOf(AppException.class);
        verifyNoInteractions(announcements, notifications);
    }

    @Test
    void inactiveOwnerCannotMutateAnnouncements() {
        teacher.setStatus(AccountStatus.SUSPENDED);
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        assertThatThrownBy(() -> service.create(teacher.getId(), classroom.getId(),
                new AnnouncementDtos.CreateRequest("Title", "Content"))).isInstanceOf(AppException.class);
        verifyNoInteractions(announcements, notifications);
    }

    @Test
    void createAnnouncement_Success_DispatchesNotificationToActiveStudents() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(announcements.save(any(AnnouncementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var membership = new ClassroomMembershipEntity();
        membership.setId(UUID.randomUUID());
        membership.setClassroom(classroom);
        membership.setStudent(student);
        membership.setStatus(MembershipStatus.ACTIVE);

        when(memberships.findByClassroomIdAndStatus(classroom.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of(membership));

        var request = new AnnouncementDtos.CreateRequest("Final Exam Info", "The exam starts next Monday.");
        var response = service.create(teacher.getId(), classroom.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Final Exam Info");
        assertThat(response.content()).isEqualTo("The exam starts next Monday.");
        assertThat(response.isPinned()).isFalse();

        verify(announcements).save(any(AnnouncementEntity.class));
        verify(notifications).notifyStudent(
                eq(student),
                eq("ANNOUNCEMENT"),
                eq("Final Exam Info"),
                eq("Final Exam Info"),
                contains("/classrooms/" + classroom.getId() + "/announcements/"),
                any(UUID.class),
                isNull()
        );
    }

    @Test
    void createAnnouncement_Fails_WhenNotOwner() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        var request = new AnnouncementDtos.CreateRequest("Hacked Title", "Hacked Content");

        assertThatThrownBy(() -> service.create(otherTeacher.getId(), classroom.getId(), request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());

        verify(announcements, never()).save(any());
        verify(notifications, never()).notifyStudent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listAnnouncements_Fails_WhenNotMemberNorOwner() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(student.getId(), classroom.getId(), PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());
    }

    @Test
    void listAnnouncements_Success_ForStudentMember() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        var membership = new ClassroomMembershipEntity();
        membership.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findByClassroomIdAndStudentId(classroom.getId(), student.getId()))
                .thenReturn(Optional.of(membership));

        var announcement = AnnouncementEntity.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .author(teacher)
                .title("Welcome")
                .content("Welcome to class!")
                .isPinned(true)
                .pinnedAt(clock.instant())
                .build();

        when(announcements.findAllByClassroomIdSorted(eq(classroom.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(announcement)));

        var page = service.list(student.getId(), classroom.getId(), PageRequest.of(0, 10));

        assertThat(page.getData().getItems()).hasSize(1);
        assertThat(page.getData().getItems().get(0).title()).isEqualTo("Welcome");
        assertThat(page.getData().getItems().get(0).isPinned()).isTrue();
    }

    @Test
    void pinAndUnpinAnnouncement_Success() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        var announcement = AnnouncementEntity.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .author(teacher)
                .title("Important")
                .content("Very important")
                .isPinned(false)
                .build();

        when(announcements.findByIdAndClassroomId(announcement.getId(), classroom.getId()))
                .thenReturn(Optional.of(announcement));
        when(announcements.save(any(AnnouncementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var pinned = service.pin(teacher.getId(), classroom.getId(), announcement.getId());
        assertThat(pinned.isPinned()).isTrue();
        assertThat(pinned.pinnedAt()).isEqualTo(clock.instant());

        var unpinned = service.unpin(teacher.getId(), classroom.getId(), announcement.getId());
        assertThat(unpinned.isPinned()).isFalse();
        assertThat(unpinned.pinnedAt()).isNull();
    }

    @Test
    void deleteAnnouncement_Fails_WhenNotOwner() {
        when(classrooms.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        var announcement = AnnouncementEntity.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .author(teacher)
                .title("Note")
                .content("Content")
                .build();

        when(announcements.findByIdAndClassroomId(announcement.getId(), classroom.getId()))
                .thenReturn(Optional.of(announcement));

        assertThatThrownBy(() -> service.delete(otherTeacher.getId(), classroom.getId(), announcement.getId()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_OPERATION.getCode());

        verify(announcements, never()).delete(any());
    }

    @Test
    void classroomScopedMutationsAcquireWritableLock() {
        var announcement = AnnouncementEntity.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .author(teacher)
                .title("Initial")
                .content("Body")
                .isPinned(false)
                .build();
        when(announcements.save(any(AnnouncementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(announcements.findByIdAndClassroomId(announcement.getId(), classroom.getId())).thenReturn(Optional.of(announcement));
        when(memberships.findByClassroomIdAndStatus(classroom.getId(), MembershipStatus.ACTIVE)).thenReturn(List.of());

        service.create(teacher.getId(), classroom.getId(), new AnnouncementDtos.CreateRequest("Title", "Body"));
        service.update(teacher.getId(), classroom.getId(), announcement.getId(), new AnnouncementDtos.UpdateRequest("Title 2", "Body 2"));
        service.pin(teacher.getId(), classroom.getId(), announcement.getId());
        service.unpin(teacher.getId(), classroom.getId(), announcement.getId());
        service.delete(teacher.getId(), classroom.getId(), announcement.getId());

        verify(classroomService, times(5)).lockWritable(classroom.getId());
        verify(classroomService, never()).lockReadable(classroom.getId());
    }
}
