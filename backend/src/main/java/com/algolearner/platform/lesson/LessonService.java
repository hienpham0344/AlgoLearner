package com.algolearner.platform.lesson;

import com.algolearner.platform.assignment.AssignmentRepository;
import com.algolearner.platform.assignment.AssignmentStatus;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final AssignmentRepository assignments;
    private final Clock clock;
    private final com.algolearner.platform.classroom.ClassroomService classroomService;
    private final List<LessonProgressProvider> progressProviders;
    private final LessonProgressService progressService;
    private final com.algolearner.platform.storage.ManagedFileService managedFiles;

    public List<LessonDtos.LessonSummaryView> list(UUID accountId, UUID classroomId) {
        var classroom = requireClassroom(classroomId);
        boolean isOwner = classroom.getOwner().getId().equals(accountId);

        List<LessonEntity> visibleLessons;
        if (!isOwner) {
            requireActiveStudent(classroomId, accountId);
            visibleLessons = lessons.findByClassroomIdAndStatusInOrderByDisplayOrderAsc(
                    classroomId, List.of(LessonStatus.PUBLISHED)
            );
        } else {
            visibleLessons = lessons.findByClassroomIdOrderByDisplayOrderAsc(classroomId);
        }
        if (visibleLessons.isEmpty()) {
            return List.of();
        }

        var lessonIds = visibleLessons.stream().map(LessonEntity::getId).toList();
        Map<UUID, LessonCompletionEntity> completionByLessonId = completions.findByLessonIdInAndStudentId(lessonIds, accountId)
                .stream()
                .filter(c -> c.getLesson() != null)
                .collect(Collectors.toMap(
                        c -> c.getLesson().getId(),
                        c -> c,
                        (first, second) -> first
                ));
        Map<UUID, Integer> readyMaterialCountByLessonId = materials.findByLessonIdIn(lessonIds)
                .stream()
                .filter(m -> m.getLesson() != null && "READY".equals(m.getMaterialState()) && m.getStoredFileId() != null)
                .collect(Collectors.toMap(
                        m -> m.getLesson().getId(),
                        m -> 1,
                        Integer::sum
                ));

        return visibleLessons.stream()
                .map(l -> toSummaryView(
                        l,
                        completionByLessonId.get(l.getId()),
                        readyMaterialCountByLessonId.getOrDefault(l.getId(), 0)))
                .toList();
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
        var classroom = classroomService.lockWritable(classroomId);
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
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        if (lesson.getStatus() == LessonStatus.ARCHIVED) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        CompletionMode previousMode = lesson.getCompletionMode();
        lesson.setTitle(request.title().trim());
        if (request.content() != null) lesson.setContent(request.content());
        if (request.codeExample() != null) lesson.setCodeExample(request.codeExample());
        if (request.externalLinks() != null) lesson.setExternalLinks(request.externalLinks());
        if (request.completionMode() == CompletionMode.AUTO_ITEMS) {
            if (lesson.getStatus() == LessonStatus.PUBLISHED && lesson.getRuleVersion() == 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Configure completion conditions before enabling automatic completion");
            }
            if (lesson.getRuleVersion() > 0 && progressService != null) {
                progressService.validateActiveRule(classroomId, lesson);
            }
        }
        if (request.completionMode() != null) lesson.setCompletionMode(request.completionMode());

        var saved = lessons.save(lesson);
        if (previousMode != CompletionMode.AUTO_ITEMS
                && saved.getCompletionMode() == CompletionMode.AUTO_ITEMS
                && saved.getRuleVersion() > 0
                && progressService != null) {
            progressService.recomputeAllActiveStudents(classroomId, saved);
        }
        return toDetailView(saved, accountId);
    }

    @Transactional
    public void delete(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        // FR-LES-005: Hard delete ONLY allowed when no progress or submissions exist
        if (lesson.getRuleVersion() > 0 || completions.existsByLessonId(lessonId) || linkedItems.countByLessonId(lessonId) > 0) {
            throw new ApiException(ErrorCode.CANNOT_HARD_DELETE_LESSON);
        }

        lessons.delete(lesson);
    }

    @Transactional
    public LessonDtos.LessonDetailView publish(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        requireStatus(lesson, LessonStatus.DRAFT);
        if (lesson.getCompletionMode() == CompletionMode.AUTO_ITEMS) {
            if (lesson.getRuleVersion() == 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Configure completion conditions before publishing");
            }
            if (progressService != null) {
                progressService.validateActiveRule(classroomId, lesson);
            }
        }
        lesson.setStatus(LessonStatus.PUBLISHED);
        return toDetailView(lessons.save(lesson), accountId);
    }

    @Transactional
    public LessonDtos.LessonDetailView unpublish(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        requireStatus(lesson, LessonStatus.PUBLISHED);
        // FR-LES-004: Chuyển về Draft giữ nguyên tiến độ đã lưu của học viên
        lesson.setStatus(LessonStatus.DRAFT);
        return toDetailView(lessons.save(lesson), accountId);
    }

    @Transactional
    public LessonDtos.LessonDetailView archive(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        if (lesson.getStatus() != LessonStatus.ARCHIVED) lesson.setArchivedFromStatus(lesson.getStatus());
        lesson.setStatus(LessonStatus.ARCHIVED);
        return toDetailView(lessons.save(lesson), accountId);
    }

    @Transactional
    public LessonDtos.LessonDetailView restore(UUID accountId, UUID classroomId, UUID lessonId) {
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        requireStatus(lesson, LessonStatus.ARCHIVED);
        LessonStatus targetStatus = lesson.getArchivedFromStatus() == LessonStatus.PUBLISHED
                ? LessonStatus.PUBLISHED
                : LessonStatus.DRAFT;
        if (targetStatus == LessonStatus.PUBLISHED && lesson.getCompletionMode() == CompletionMode.AUTO_ITEMS) {
            if (lesson.getRuleVersion() == 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Configure completion conditions before publishing");
            }
            if (progressService != null) {
                progressService.validateActiveRule(classroomId, lesson);
            }
        }
        lesson.setStatus(targetStatus);
        lesson.setArchivedFromStatus(null);
        return toDetailView(lessons.save(lesson), accountId);
    }

    @Transactional
    public LessonDtos.MaterialView addMaterial(UUID accountId, UUID classroomId, UUID lessonId, LessonDtos.AddMaterialRequest request) {
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));
        if (lesson.getStatus() == LessonStatus.ARCHIVED) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);

        if (request.fileId() == null) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Upload lesson material before attaching it");
        var file = managedFiles.requireReady(accountId, classroomId, request.fileId(), com.algolearner.platform.storage.FilePurpose.LESSON_MATERIAL);
        var material = LessonMaterialEntity.builder()
                .id(UUID.randomUUID())
                .lesson(lesson)
                .fileName(file.name())
                .fileUrl("/api/v1/files/" + file.id() + "/content")
                .fileType(MaterialFileType.valueOf(file.kind().name()))
                .fileSize(file.size())
                .storedFileId(file.id())
                .materialState("READY")
                .build();

        var saved = materials.save(material);
        return new LessonDtos.MaterialView(saved.getId(), saved.getFileName(), saved.getFileUrl(), saved.getFileType(), saved.getFileSize(), saved.getCreatedAt());
    }

    @Transactional
    public void removeMaterial(UUID accountId, UUID classroomId, UUID lessonId, UUID materialId) {
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));
        if (lesson.getStatus() == LessonStatus.ARCHIVED) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);

        var material = materials.findByIdAndLessonId(materialId, lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson material not found"));
        materials.delete(material);
    }

    @Transactional
    public LessonDtos.LinkedItemView linkItem(UUID accountId, UUID classroomId, UUID lessonId, LessonDtos.LinkItemRequest request) {
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));
        if (lesson.getStatus() == LessonStatus.ARCHIVED) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);

        if (linkedItems.existsByLessonIdAndItemTypeAndItemId(lessonId, request.itemType(), request.itemId()))
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Item is already linked");
        if (request.itemType() == LinkedItemType.QUIZ && linkedItems.existsByItemTypeAndItemId(LinkedItemType.QUIZ, request.itemId()))
            throw new ApiException(ErrorCode.QUIZ_ALREADY_LINKED);
        var provider = provider(request.itemType());
        boolean exists = provider != null ? provider.existsInClassroom(request.itemId(), classroomId)
                : request.itemType() == LinkedItemType.ASSIGNMENT && assignments.findByIdAndClassroomId(request.itemId(), classroomId).isPresent();
        if (!exists) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Linked item is unavailable in this classroom");

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
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, accountId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));
        if (lesson.getStatus() == LessonStatus.ARCHIVED) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);

        var item = linkedItems.findByLessonIdAndItemId(lessonId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Linked item not found"));

        if (progressService.ruleReferences(lessonId, item.getItemType(), item.getItemId()))
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Update completion rule before unlinking this item");
        linkedItems.delete(item);
    }

    @Transactional
    public void completeLesson(UUID studentId, UUID classroomId, UUID lessonId) {
        var classroom = classroomService.lockReadable(classroomId);
        if (classroom.getStatus() != com.algolearner.platform.classroom.ClassroomStatus.ACTIVE
                || classroom.getOwner().getStatus() != com.algolearner.platform.auth.AccountStatus.ACTIVE)
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        requireActiveStudentForUpdate(classroomId, studentId);

        var lesson = lessons.findByIdAndClassroomId(lessonId, classroomId)
                .orElseThrow(() -> new ApiException(ErrorCode.LESSON_NOT_FOUND));

        if (lesson.getStatus() != LessonStatus.PUBLISHED || lesson.getCompletionMode() != CompletionMode.MANUAL) {
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

        if (completion.isCompleted()) return;
        completion.setCompleted(true);
        completion.setCompletedAt(clock.instant());
        completions.save(completion);
        progressService.recordManual(lesson, studentId);
    }

    public LessonDtos.CompletionRuleView completionRule(UUID actorId, UUID classroomId, UUID lessonId) {
        requireOwnerForRead(requireClassroom(classroomId), actorId);
        if (lessons.findByIdAndClassroomId(lessonId,classroomId).isEmpty()) throw new ApiException(ErrorCode.LESSON_NOT_FOUND);
        return progressService.rule(lessonId);
    }

    @Transactional
    public void configureCompletionRule(UUID actorId, UUID classroomId, UUID lessonId, LessonCompletionRule rule) {
        var classroom = classroomService.lockWritable(classroomId);
        requireOwner(classroom, actorId);
        progressService.configure(classroomId, lessonId, rule);
    }

    public LessonDtos.CapabilitiesView capabilities(UUID accountId, UUID classroomId) {
        var classroom = requireClassroom(classroomId);
        requireOwnerForRead(classroom, accountId);
        var upload = managedFiles != null ? managedFiles.capabilities() : null;
        boolean activityLinkingAvailable = (progressProviders != null && !progressProviders.isEmpty()) || assignments != null;
        return new LessonDtos.CapabilitiesView(
                upload != null && upload.uploadsAvailable(),
                activityLinkingAvailable,
                upload != null ? upload.maxFileSizeBytes() : 0L,
                upload != null ? upload.allowedExtensions() : List.of()
        );
    }

    public List<LessonDtos.ActivityOptionView> activities(UUID accountId, UUID classroomId) {
        var classroom = requireClassroom(classroomId);
        requireOwnerForRead(classroom, accountId);
        var linkedLessonByItem = linkedItems.findByLessonClassroomId(classroomId).stream()
                .filter(item -> item.getLesson() != null)
                .collect(Collectors.toMap(
                        item -> new ItemKey(item.getItemType(), item.getItemId()),
                        item -> item.getLesson().getId(),
                        (first, second) -> first
                ));
        return classroomItemSummaries(classroomId).entrySet().stream()
                .map(entry -> new LessonDtos.ActivityOptionView(
                        entry.getKey().id(),
                        entry.getKey().type(),
                        entry.getValue().title(),
                        linkedLessonByItem.get(entry.getKey()),
                        entry.getValue().availableToStudent()
                ))
                .toList();
    }

    @Transactional
    public void recomputeForItem(UUID classroomId, LinkedItemType type, UUID itemId, UUID studentId) {
        classroomService.lockReadable(classroomId);
        progressService.recomputeForItem(classroomId, type, itemId, studentId);
    }

    public com.algolearner.platform.common.dto.PageResponse<LessonDtos.StudentProgressView> studentProgress(
            UUID actorId, UUID classroomId, UUID lessonId, org.springframework.data.domain.Pageable pageable) {
        var classroom = requireClassroom(classroomId);
        requireOwnerForRead(classroom, actorId);
        if (lessons.findByIdAndClassroomId(lessonId, classroomId).isEmpty()) throw new ApiException(ErrorCode.LESSON_NOT_FOUND);
        var page = memberships.findByClassroomIdAndStatus(classroomId, MembershipStatus.ACTIVE, pageable);
        var studentIds = page.getContent().stream().map(member -> member.getStudent().getId()).toList();
        Map<UUID, LessonCompletionEntity> completionsByStudent = studentIds.isEmpty()
                ? Map.of()
                : completions.findByLessonIdAndStudentIdIn(lessonId, studentIds).stream()
                        .filter(c -> c.getStudent() != null)
                        .collect(Collectors.toMap(
                                c -> c.getStudent().getId(),
                                c -> c,
                                (first, second) -> first
                        ));
        return com.algolearner.platform.common.dto.PageResponse.fromPage(
                page.map(member -> {
                    var completion = completionsByStudent.get(member.getStudent().getId());
                    return new LessonDtos.StudentProgressView(member.getStudent().getId(), member.getStudent().getUsername(),
                            completion != null && completion.isCompleted(),
                            completion != null ? completion.getCompletedAt() : null);
                }));
    }

    LessonProgressProvider provider(LinkedItemType type) {
        return progressProviders.stream().filter(provider -> provider.itemType() == type).findFirst().orElse(null);
    }
    private void requireStatus(LessonEntity lesson, LessonStatus status) {
        if (lesson.getStatus() != status) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid lesson state transition");
    }

    private ClassroomEntity requireClassroom(UUID classroomId) {
        return classrooms.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));
    }

    private void requireOwnerForRead(ClassroomEntity classroom, UUID accountId) {
        if (!classroom.getOwner().getId().equals(accountId)) throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
    }
    private void requireOwner(ClassroomEntity classroom, UUID accountId) {
        if (classroom.getStatus() != com.algolearner.platform.classroom.ClassroomStatus.ACTIVE
                || classroom.getOwner().getStatus() != com.algolearner.platform.auth.AccountStatus.ACTIVE)
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
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

    private void requireActiveStudentForUpdate(UUID classroomId, UUID accountId) {
        var membership = memberships.findByClassroomIdAndStudentIdForUpdate(classroomId, accountId);
        if (membership.isEmpty()) {
            membership = memberships.findByClassroomIdAndStudentId(classroomId, accountId);
        }
        boolean active = membership
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
        if (!active) {
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        }
    }

    private LessonDtos.LessonSummaryView toSummaryView(LessonEntity l, LessonCompletionEntity completion, int materialCount) {
        Boolean completed = completion != null ? completion.isCompleted() : null;
        java.time.Instant completedAt = completion != null ? completion.getCompletedAt() : null;
        return new LessonDtos.LessonSummaryView(
                l.getId(), l.getClassroom().getId(), l.getTitle(), l.getStatus(),
                l.getCompletionMode(), l.getDisplayOrder(), completed, completedAt,
                l.getCreatedAt(), l.getUpdatedAt(), l.getContent(), l.getCodeExample(), materialCount
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

        var matViews = materials.findByLessonId(l.getId()).stream().filter(m -> "READY".equals(m.getMaterialState()) && m.getStoredFileId() != null).map(m ->
                new LessonDtos.MaterialView(m.getId(), m.getFileName(), m.getFileUrl(), m.getFileType(), m.getFileSize(), m.getCreatedAt())
        ).toList();

        boolean isOwner = l.getClassroom().getOwner().getId().equals(accountId);
        var rawLinkedItems = linkedItems.findByLessonIdOrderByDisplayOrderAsc(l.getId());
        var visibleRawItems = isOwner ? rawLinkedItems : rawLinkedItems.stream()
                .filter(item -> provider(item.getItemType()) != null
                        && provider(item.getItemType()).availableToStudent(item.getItemId(), l.getClassroom().getId(), accountId))
                .toList();
        var itemSummaries = visibleRawItems.isEmpty()
                ? Map.<ItemKey, LessonProgressProvider.ClassroomItemSummary>of()
                : classroomItemSummaries(l.getClassroom().getId());

        LessonDtos.CompletionRuleView ruleView = (l.getCompletionMode() == CompletionMode.AUTO_ITEMS
                && l.getRuleVersion() > 0 && progressService != null)
                ? progressService.rule(l.getId())
                : null;
        Map<ItemKey, List<LessonCompletionRule.Condition>> conditionsByItem =
                (ruleView != null && ruleView.rule() != null && ruleView.rule().conditions() != null)
                        ? ruleView.rule().conditions().stream()
                                .filter(c -> c != null && c.itemType() != null && c.itemId() != null)
                                .collect(Collectors.groupingBy(c -> new ItemKey(c.itemType(), c.itemId())))
                        : Map.of();

        var linkViews = visibleRawItems.stream()
                .map(item -> {
                    var key = new ItemKey(item.getItemType(), item.getItemId());
                    var summary = itemSummaries.get(key);
                    String title = summary != null ? summary.title() : null;
                    boolean available = isOwner ? (summary != null && summary.availableToStudent()) : true;
                    Boolean itemCompleted;
                    if (isOwner) {
                        itemCompleted = false;
                    } else {
                        LessonProgressProvider p = provider(item.getItemType());
                        LessonProgressSource source = p != null
                                ? p.progress(item.getItemId(), l.getClassroom().getId(), accountId)
                                : null;
                        List<LessonCompletionRule.Condition> itemConditions = conditionsByItem.get(key);
                        if (itemConditions != null && !itemConditions.isEmpty()) {
                            boolean isAny = ruleView != null && ruleView.rule() != null
                                    && ruleView.rule().operator() == LessonCompletionRule.Operator.ANY;
                            itemCompleted = source != null && (isAny
                                    ? itemConditions.stream().anyMatch(cond -> conditionSatisfied(cond, source))
                                    : itemConditions.stream().allMatch(cond -> conditionSatisfied(cond, source)));
                        } else {
                            itemCompleted = source != null && source.submitted();
                        }
                    }
                    return new LessonDtos.LinkedItemView(
                            item.getId(), item.getItemType(), item.getItemId(), item.getDisplayOrder(), title, available, itemCompleted);
                })
                .toList();

        boolean canDelete = isOwner
                && l.getRuleVersion() == 0
                && !completions.existsByLessonId(l.getId())
                && linkedItems.countByLessonId(l.getId()) == 0;
        LessonStatus previousStatus = isOwner ? l.getArchivedFromStatus() : null;

        var visibleItemKeys = linkViews.stream()
                .map(item -> new ItemKey(item.itemType(), item.itemId()))
                .collect(Collectors.toSet());
        List<UUID> requiredItemIds = List.of();
        if (ruleView != null && ruleView.rule() != null && ruleView.rule().conditions() != null) {
            requiredItemIds = ruleView.rule().conditions().stream()
                    .filter(condition -> isOwner || visibleItemKeys.contains(new ItemKey(condition.itemType(), condition.itemId())))
                    .map(LessonCompletionRule.Condition::itemId)
                    .distinct()
                    .toList();
        }

        return new LessonDtos.LessonDetailView(
                l.getId(), l.getClassroom().getId(), l.getTitle(), l.getContent(), l.getCodeExample(),
                l.getExternalLinks(), l.getStatus(), l.getCompletionMode(), l.getDisplayOrder(),
                matViews, linkViews, completed, completedAt, l.getCreatedAt(), l.getUpdatedAt(),
                previousStatus, canDelete, requiredItemIds
        );
    }

    private static boolean conditionSatisfied(LessonCompletionRule.Condition condition, LessonProgressSource source) {
        if (condition == null || condition.requirement() == null || source == null) {
            return false;
        }
        return switch (condition.requirement()) {
            case SUBMITTED -> source.submitted();
            case SCORE_AT_LEAST -> source.publishedFinalEvaluation()
                    && source.scorePercent() != null
                    && condition.scorePercent() != null
                    && source.scorePercent().compareTo(condition.scorePercent()) >= 0;
        };
    }

    private Map<ItemKey, LessonProgressProvider.ClassroomItemSummary> classroomItemSummaries(UUID classroomId) {
        var summaries = new LinkedHashMap<ItemKey, LessonProgressProvider.ClassroomItemSummary>();
        if (progressProviders != null) {
            for (var p : progressProviders) {
                for (var item : p.classroomItems(classroomId)) {
                    summaries.putIfAbsent(new ItemKey(p.itemType(), item.id()), item);
                }
            }
        }
        boolean hasAssignmentProvider = progressProviders != null
                && progressProviders.stream().anyMatch(p -> p.itemType() == LinkedItemType.ASSIGNMENT);
        if (!hasAssignmentProvider && assignments != null) {
            for (var assignment : assignments.findByClassroomIdOrderByCreatedAtDesc(classroomId)) {
                summaries.putIfAbsent(new ItemKey(LinkedItemType.ASSIGNMENT, assignment.getId()),
                        new LessonProgressProvider.ClassroomItemSummary(
                                assignment.getId(),
                                assignment.getTitle(),
                                assignment.getStatus() == AssignmentStatus.PUBLISHED));
            }
        }
        return summaries;
    }

    private record ItemKey(LinkedItemType type, UUID id) { }
}
