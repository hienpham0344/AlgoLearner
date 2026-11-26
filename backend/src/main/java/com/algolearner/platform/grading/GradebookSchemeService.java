package com.algolearner.platform.grading;

import com.algolearner.platform.audit.AuditService.RequestContext;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.grading.GradeSchemeRevisionEntity.Mode;
import com.algolearner.platform.grading.GradebookDtos.ItemReference;
import com.algolearner.platform.grading.GradebookDtos.SchemeApplyRequest;
import com.algolearner.platform.grading.GradebookDtos.SchemeItem;
import com.algolearner.platform.grading.GradebookDtos.SchemePreviewRequest;
import com.algolearner.platform.grading.GradebookDtos.SchemeView;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradebookSchemeService {
    private static final int WHOLE_WEIGHT_UNITS = 1_000_000;
    private static final int SCALE = 4;
    private static final BigDecimal WHOLE_WEIGHT = BigDecimal.valueOf(WHOLE_WEIGHT_UNITS, SCALE);
    private static final Comparator<ItemReference> ITEM_ORDER = Comparator.comparing(ItemReference::sourceType)
            .thenComparing(ItemReference::sourceId);

    private final GradebookAccess access;
    private final GradebookRepository gradebooks;
    private final GradeSchemeRevisionRepository revisions;
    private final GradeSchemeItemRepository items;
    private final ApplicationEventPublisher events;
    private final GradebookReadRepository reads;

    public SchemeView get(Authentication principal, UUID classroomId) {
        access.requireTeacherRead(principal, classroomId);
        return active(find(classroomId));
    }

    public List<SchemeItem> preview(Authentication principal, UUID classroomId, SchemePreviewRequest request) {
        access.requireTeacherRead(principal, classroomId);
        validateScope(classroomId, request.items());
        var ordered = request.items().stream().sorted(ITEM_ORDER).toList();
        int count = ordered.size();
        if (count == 0) return List.of();
        int share = WHOLE_WEIGHT_UNITS / count;
        int remainder = WHOLE_WEIGHT_UNITS % count;
        return IntStream.range(0, count).mapToObj(index -> {
            var item = ordered.get(index);
            var weight = BigDecimal.valueOf(share + (index < remainder ? 1 : 0), SCALE);
            return new SchemeItem(item.sourceType(), item.sourceId(), weight, true);
        }).toList();
    }

    @Transactional
    public SchemeView apply(Authentication principal, UUID classroomId, SchemeApplyRequest request) {
        return apply(principal, classroomId, request, RequestContext.EMPTY);
    }

    @Transactional
    public SchemeView apply(Authentication principal, UUID classroomId, SchemeApplyRequest request, RequestContext context) {
        var actor = access.requireTeacherWrite(principal, classroomId);
        var gradebook = lock(classroomId);
        requireVersion(gradebook, request.expectedVersion());
        var configured = request.items().stream().map(item -> normalize(request.mode(), item)).toList();
        requireValidWeights(request.mode(), configured);
        var references = configured.stream().map(SchemeItem::reference).toList();
        validateScope(classroomId, references);
        var ungraded = new HashSet<>(reads.ungradedItems(classroomId));
        var invalidItems = references.stream().filter(ungraded::contains)
                .map(item -> item.sourceType() + " " + item.sourceId()).toList();
        if (!invalidItems.isEmpty()) {
            throw new ApiException(ErrorCode.GRADEBOOK_ITEM_UNGRADED, "Ungraded items: " + String.join(", ", invalidItems));
        }

        var previous = active(gradebook);
        var revision = new GradeSchemeRevisionEntity();
        revision.setId(UUID.randomUUID());
        revision.setGradebookId(gradebook.getId());
        revision.setRevisionNo(previous.revisionNumber() == null ? 1 : previous.revisionNumber() + 1);
        revision.setMode(request.mode());
        revision.setCreatedBy(actor);
        revision.setReason(request.reason());
        revision = revisions.saveAndFlush(revision);
        var revisionId = revision.getId();
        items.saveAll(configured.stream().map(item -> persistable(revisionId, item)).toList());
        gradebook.setActiveSchemeRevisionId(revisionId);
        gradebooks.flush();
        var applied = view(gradebook, revision, configured);
        events.publishEvent(new GradebookHistoryService.SchemeApplied(classroomId, gradebook.getId(), actor, previous, applied, context));
        return applied;
    }

    GradebookEntity find(UUID classroomId) {
        return gradebooks.findByClassroomId(classroomId).orElseThrow(GradebookSchemeService::notFound);
    }

    GradebookEntity lock(UUID classroomId) {
        return gradebooks.findLockedByClassroomId(classroomId).orElseThrow(GradebookSchemeService::notFound);
    }

    SchemeView active(GradebookEntity gradebook) {
        var revisionId = gradebook.getActiveSchemeRevisionId();
        if (revisionId == null) {
            return new SchemeView(null, null, null, gradebook.getVersion(), List.of(), null, null, null);
        }
        var revision = revisions.findByIdAndGradebookId(revisionId, gradebook.getId())
                .orElseThrow(GradebookSchemeService::notFound);
        var configured = items.findAllByRevisionId(revisionId).stream()
                .map(item -> new SchemeItem(item.getSourceType(), item.getSourceId(), item.getWeight(), item.isRequired()))
                .toList();
        return view(gradebook, revision, configured);
    }

    static void requireVersion(GradebookEntity gradebook, long expectedVersion) {
        if (gradebook.getVersion() != expectedVersion) throw new ApiException(ErrorCode.GRADEBOOK_VERSION_CONFLICT);
    }

    static void requireValidWeights(Mode mode, List<SchemeItem> configured) {
        if (mode == Mode.WEIGHTED && configured.stream().map(SchemeItem::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(WHOLE_WEIGHT) != 0) {
            throw new ApiException(ErrorCode.GRADEBOOK_INVALID_WEIGHTS);
        }
    }

    private void validateScope(UUID classroomId, List<ItemReference> requested) {
        if (requested.stream().distinct().count() != requested.size()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Duplicate gradebook item");
        }
        requested.forEach(item -> access.requireItemInClass(classroomId, item.sourceType(), item.sourceId()));
        var requestedItems = new HashSet<>(requested);
        if (reads.hiddenItems(classroomId).stream().anyMatch(requestedItems::contains)) {
            throw new ApiException(ErrorCode.GRADEBOOK_ITEM_HIDDEN);
        }
    }

    private static SchemeItem normalize(Mode mode, SchemeItem item) {
        var weight = mode == Mode.RAW ? BigDecimal.ZERO : item.weight();
        if (weight == null || weight.signum() < 0 || weight.compareTo(WHOLE_WEIGHT) > 0
                || weight.stripTrailingZeros().scale() > SCALE) {
            throw new ApiException(ErrorCode.GRADEBOOK_INVALID_WEIGHTS, "Weight must be between 0 and 100 with up to 4 decimal places");
        }
        return new SchemeItem(item.sourceType(), item.sourceId(), weight.setScale(SCALE), item.required());
    }

    private static GradeSchemeItemEntity persistable(UUID revision, SchemeItem value) {
        var item = new GradeSchemeItemEntity();
        item.setId(UUID.randomUUID());
        item.setRevisionId(revision);
        item.setSourceId(value.sourceId());
        item.setSourceType(value.sourceType());
        item.setWeight(value.weight());
        item.setRequired(value.required());
        return item;
    }

    private static SchemeView view(GradebookEntity gradebook, GradeSchemeRevisionEntity revision, List<SchemeItem> configured) {
        return new SchemeView(revision.getId(), revision.getRevisionNo(), revision.getMode(), gradebook.getVersion(),
                configured.stream().sorted(Comparator.comparing(SchemeItem::reference, ITEM_ORDER)).toList(),
                revision.getReason(), revision.getCreatedBy(), revision.getCreatedAt());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.GRADEBOOK_NOT_FOUND);
    }
}
