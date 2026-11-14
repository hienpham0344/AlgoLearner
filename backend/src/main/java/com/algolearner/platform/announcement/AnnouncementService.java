package com.algolearner.platform.announcement;

import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.common.exception.ResourceNotFoundException;
import com.algolearner.platform.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

    private final AnnouncementRepository announcements;
    private final ClassroomRepository classrooms;
    private final ClassroomMembershipRepository memberships;
    private final AccountRepository accounts;
    private final NotificationService notifications;
    private final Clock clock;

    public PageResponse<AnnouncementDtos.AnnouncementView> list(UUID accountId, UUID classroomId, Pageable pageable) {
        var classroom = requireClassroom(classroomId);
        requireViewAccess(classroom, accountId);
        var page = announcements.findAllByClassroomIdSorted(classroomId, pageable);
        return PageResponse.fromPage(page.map(this::toView));
    }

    public AnnouncementDtos.AnnouncementView get(UUID accountId, UUID classroomId, UUID announcementId) {
        var classroom = requireClassroom(classroomId);
        requireViewAccess(classroom, accountId);
        var announcement = announcements.findByIdAndClassroomId(announcementId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
        return toView(announcement);
    }

    @Transactional
    public AnnouncementDtos.AnnouncementView create(UUID accountId, UUID classroomId, AnnouncementDtos.CreateRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var author = classroom.getOwner();
        var announcement = AnnouncementEntity.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .author(author)
                .title(request.title().trim())
                .content(request.content().trim())
                .isPinned(false)
                .pinnedAt(null)
                .build();

        var saved = announcements.save(announcement);

        // In-app Notification for all active students in the classroom (FR-ANN-003)
        var activeMembers = memberships.findByClassroomIdAndStatus(classroomId, MembershipStatus.ACTIVE);
        for (var member : activeMembers) {
            notifications.notifyStudent(
                    member.getStudent(),
                    "ANNOUNCEMENT",
                    "New announcement: " + saved.getTitle(),
                    saved.getTitle(),
                    "/classrooms/" + classroomId + "/announcements/" + saved.getId(),
                    saved.getId(),
                    null
            );
        }

        return toView(saved);
    }

    @Transactional
    public AnnouncementDtos.AnnouncementView update(UUID accountId, UUID classroomId, UUID announcementId, AnnouncementDtos.UpdateRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var announcement = announcements.findByIdAndClassroomId(announcementId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        announcement.setTitle(request.title().trim());
        announcement.setContent(request.content().trim());
        return toView(announcements.save(announcement));
    }

    @Transactional
    public void delete(UUID accountId, UUID classroomId, UUID announcementId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var announcement = announcements.findByIdAndClassroomId(announcementId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        announcements.delete(announcement);
    }

    @Transactional
    public AnnouncementDtos.AnnouncementView pin(UUID accountId, UUID classroomId, UUID announcementId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var announcement = announcements.findByIdAndClassroomId(announcementId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        announcement.setPinned(true);
        announcement.setPinnedAt(clock.instant());
        return toView(announcements.save(announcement));
    }

    @Transactional
    public AnnouncementDtos.AnnouncementView unpin(UUID accountId, UUID classroomId, UUID announcementId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var announcement = announcements.findByIdAndClassroomId(announcementId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        announcement.setPinned(false);
        announcement.setPinnedAt(null);
        return toView(announcements.save(announcement));
    }

    private ClassroomEntity requireClassroom(UUID classroomId) {
        return classrooms.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));
    }

    private void requireOwner(ClassroomEntity classroom, UUID accountId) {
        if (!classroom.getOwner().getId().equals(accountId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }
    }

    private void requireViewAccess(ClassroomEntity classroom, UUID accountId) {
        if (classroom.getOwner().getId().equals(accountId)) {
            return;
        }
        boolean isMember = memberships.findByClassroomIdAndStudentId(classroom.getId(), accountId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
        if (!isMember) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }
    }

    private AnnouncementDtos.AnnouncementView toView(AnnouncementEntity entity) {
        String authorName = entity.getAuthor().getFullName() != null
                ? entity.getAuthor().getFullName()
                : entity.getAuthor().getUsername();
        return new AnnouncementDtos.AnnouncementView(
                entity.getId(),
                entity.getClassroom().getId(),
                entity.getAuthor().getId(),
                authorName,
                entity.getTitle(),
                entity.getContent(),
                entity.isPinned(),
                entity.getPinnedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
