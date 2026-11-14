package com.algolearner.platform.lesson;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.classroom.ClassroomEntity;
import com.algolearner.platform.classroom.ClassroomMembershipRepository;
import com.algolearner.platform.classroom.ClassroomRepository;
import com.algolearner.platform.classroom.MembershipStatus;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonService {

    private final LessonRepository lessons;
    private final LessonMaterialRepository materials;
    private final LessonLinkedItemRepository linkedItems;
    private final LessonCompletionRepository completions;
    private final ClassroomRepository classrooms;
    private final ClassroomMembershipRepository memberships;
    private final AccountRepository accounts;
    private final Clock clock;

    public List<LessonDtos.LessonSummaryView> list(UUID accountId, UUID classroomId) {
        var classroom = requireClassroom(classroomId);
        boolean isOwner = classroom.getOwner().getId().equals(accountId);

        if (!isOwner) {
            requireActiveStudent(classroomId, accountId);
            var publishedLessons = lessons.findByClassroomIdAndStatusInOrderByDisplayOrderAsc(
                    classroomId, List.of(LessonStatus.PUBLISHED)
            );
            return publishedLessons.stream().map(l -> toSummaryView(l, accountId)).toList();
        }

        var allLessons = lessons.findByClassroomIdOrderByDisplayOrderAsc(classroomId);
        return allLessons.stream().map(l -> toSummaryView(l, accountId)).toList();
    }

    public LessonDtos.LessonDetailView get(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = requireClassroom(classroomId);
        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        boolean isOwner = classroom.getOwner().getId().equals(accountId);
        if (!isOwner) {
            requireActiveStudent(classroomId, accountId);
            if (lesson.getStatus() != LessonStatus.PUBLISHED) {
                throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
            }
        }

        return toDetailView(lesson, accountId);
    }

    @Transactional
    public LessonDtos.LessonDetailView create(UUID accountId, UUID classroomId, LessonDtos.CreateLessonRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        int nextOrder = (int) lessons.countByClassroomId(classroomId);
        var lesson = LessonEntity.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .title(request.title().trim())
                .content(request.content())
                .codeExample(request.codeExample())
                .externalLinks(request.externalLinks() != null ? request.externalLinks() : "[]")
                .status(LessonStatus.DRAFT)
                .completionMode(request.completionMode() != null ? request.completionMode() : CompletionMode.MANUAL)
                .displayOrder(nextOrder)
                .build();

        return toDetailView(lessons.save(lesson), accountId);
    }

    @Transactional
    public LessonDtos.LessonDetailView update(UUID accountId, UUID classroomId, UUID lessonId, LessonDtos.UpdateLessonRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        lesson.setTitle(request.title().trim());
        if (request.content() != null) lesson.setContent(request.content());
        if (request.codeExample() != null) lesson.setCodeExample(request.codeExample());
        if (request.externalLinks() != null) lesson.setExternalLinks(request.externalLinks());
        if (request.completionMode() != null) lesson.setCompletionMode(request.completionMode());

        return toDetailView(lessons.save(lesson), accountId);
    }

    @Transactional
    public void delete(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        // FR-LES-005: Hard delete ONLY allowed when no progress or submissions exist
        if (completions.existsByLessonId(lessonId) || linkedItems.countByLessonId(lessonId) > 0) {
            throw new ApiException(ErrorCode.CANNOT_HARD_DELETE_LESSON);
        }

        lessons.delete(lesson);
    }

    @Transactional
    public LessonDtos.LessonDetailView publish(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        lesson.setStatus(LessonStatus.PUBLISHED);
        return toDetailView(lessons.save(lesson), accountId);
    }

    @Transactional
    public LessonDtos.LessonDetailView unpublish(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        // FR-LES-004: Chuyển về Draft giữ nguyên tiến độ đã lưu của học viên
        lesson.setStatus(LessonStatus.DRAFT);
        return toDetailView(lessons.save(lesson), accountId);
    }

    @Transactional
    public LessonDtos.LessonDetailView archive(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        lesson.setStatus(LessonStatus.ARCHIVED);
        return toDetailView(lessons.save(lesson), accountId);
    }

    @Transactional
    public LessonDtos.LessonDetailView restore(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        lesson.setStatus(LessonStatus.PUBLISHED);
        return toDetailView(lessons.save(lesson), accountId);
    }

    @Transactional
    public LessonDtos.MaterialView addMaterial(UUID accountId, UUID classroomId, UUID lessonId, LessonDtos.AddMaterialRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        var material = LessonMaterialEntity.builder()
                .id(UUID.randomUUID())
                .lesson(lesson)
                .fileName(request.fileName())
                .fileUrl(request.fileUrl())
                .fileType(request.fileType())
                .fileSize(request.fileSize())
                .build();

        var saved = materials.save(material);
        return new LessonDtos.MaterialView(saved.getId(), saved.getFileName(), saved.getFileUrl(), saved.getFileType(), saved.getFileSize(), saved.getCreatedAt());
    }

    @Transactional
    public LessonDtos.LinkedItemView linkItem(UUID accountId, UUID classroomId, UUID lessonId, LessonDtos.LinkItemRequest request) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        // BR-LES-002: Mỗi Quiz chỉ liên kết tối đa với một Lesson
        if (request.itemType() == LinkedItemType.QUIZ && linkedItems.existsByItemTypeAndItemId(LinkedItemType.QUIZ, request.itemId())) {
            throw new ApiException(ErrorCode.QUIZ_ALREADY_LINKED);
        }

        int nextOrder = (int) linkedItems.countByLessonId(lessonId);
        var linked = LessonLinkedItemEntity.builder()
                .id(UUID.randomUUID())
                .lesson(lesson)
                .itemType(request.itemType())
                .itemId(request.itemId())
                .displayOrder(nextOrder)
                .build();

        var saved = linkedItems.save(linked);
        return new LessonDtos.LinkedItemView(saved.getId(), saved.getItemType(), saved.getItemId(), saved.getDisplayOrder());
    }

    @Transactional
    public void unlinkItem(UUID accountId, UUID classroomId, UUID lessonId, UUID itemId) {
        var classroom = requireClassroom(classroomId);
        requireOwner(classroom, accountId);

        var item = linkedItems.findByLessonIdAndItemId(lessonId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Linked item not found"));

        linkedItems.delete(item);
    }

    @Transactional
    public void completeLesson(UUID studentId, UUID classroomId, UUID lessonId) {
        var classroom = requireClassroom(classroomId);
        requireActiveStudent(classroomId, studentId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        if (lesson.getStatus() != LessonStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }

        var student = accounts.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        var completion = completions.findByLessonIdAndStudentId(lessonId, studentId)
                .orElseGet(() -> LessonCompletionEntity.builder()
                        .id(UUID.randomUUID())
                        .lesson(lesson)
                        .student(student)
                        .build());

        completion.setCompleted(true);
        completion.setCompletedAt(clock.instant());
        completions.save(completion);
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

    private void requireActiveStudent(UUID classroomId, UUID accountId) {
        boolean active = memberships.findByClassroomIdAndStudentId(classroomId, accountId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
        if (!active) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }
    }

    private LessonDtos.LessonSummaryView toSummaryView(LessonEntity l, UUID accountId) {
        Boolean completed = null;
        java.time.Instant completedAt = null;
        var comp = completions.findByLessonIdAndStudentId(l.getId(), accountId);
        if (comp.isPresent()) {
            completed = comp.get().isCompleted();
            completedAt = comp.get().getCompletedAt();
        }
        return new LessonDtos.LessonSummaryView(
                l.getId(), l.getClassroom().getId(), l.getTitle(), l.getStatus(),
                l.getCompletionMode(), l.getDisplayOrder(), completed, completedAt,
                l.getCreatedAt(), l.getUpdatedAt()
        );
    }

    private LessonDtos.LessonDetailView toDetailView(LessonEntity l, UUID accountId) {
        Boolean completed = null;
        java.time.Instant completedAt = null;
        var comp = completions.findByLessonIdAndStudentId(l.getId(), accountId);
        if (comp.isPresent()) {
            completed = comp.get().isCompleted();
            completedAt = comp.get().getCompletedAt();
        }

        var matViews = materials.findByLessonId(l.getId()).stream().map(m ->
                new LessonDtos.MaterialView(m.getId(), m.getFileName(), m.getFileUrl(), m.getFileType(), m.getFileSize(), m.getCreatedAt())
        ).toList();

        var linkViews = linkedItems.findByLessonIdOrderByDisplayOrderAsc(l.getId()).stream().map(item ->
                new LessonDtos.LinkedItemView(item.getId(), item.getItemType(), item.getItemId(), item.getDisplayOrder())
        ).toList();

        return new LessonDtos.LessonDetailView(
                l.getId(), l.getClassroom().getId(), l.getTitle(), l.getContent(), l.getCodeExample(),
                l.getExternalLinks(), l.getStatus(), l.getCompletionMode(), l.getDisplayOrder(),
                matViews, linkViews, completed, completedAt, l.getCreatedAt(), l.getUpdatedAt()
        );
    }
}
