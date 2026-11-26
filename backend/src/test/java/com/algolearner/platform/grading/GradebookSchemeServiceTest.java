package com.algolearner.platform.grading;

import static com.algolearner.platform.grading.GradeSchemeItemEntity.SourceType.ASSIGNMENT;
import static com.algolearner.platform.grading.GradeSchemeRevisionEntity.Mode.WEIGHTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.grading.GradebookDtos.ItemReference;
import com.algolearner.platform.grading.GradebookDtos.SchemeApplyRequest;
import com.algolearner.platform.grading.GradebookDtos.SchemeItem;
import com.algolearner.platform.grading.GradebookDtos.SchemePreviewRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class GradebookSchemeServiceTest {
    private final UUID classroom = UUID.randomUUID();
    private final Authentication principal = mock(Authentication.class);
    private final GradebookAccess access = mock(GradebookAccess.class);
    private final GradebookRepository gradebooks = mock(GradebookRepository.class);
    private final GradeSchemeRevisionRepository revisions = mock(GradeSchemeRevisionRepository.class);
    private final GradeSchemeItemRepository items = mock(GradeSchemeItemRepository.class);
    private final GradebookSchemeService service = new GradebookSchemeService(access, gradebooks, revisions, items,
            mock(org.springframework.context.ApplicationEventPublisher.class), mock(GradebookReadRepository.class));

    @Test
    void previewDistributesFourDecimalRemainderInStableOrder() {
        var one = new ItemReference(ASSIGNMENT, new UUID(0, 1));
        var two = new ItemReference(ASSIGNMENT, new UUID(0, 2));
        var three = new ItemReference(ASSIGNMENT, new UUID(0, 3));
        var preview = service.preview(principal, classroom, new SchemePreviewRequest(List.of(three, two, one)));
        assertThat(preview).extracting(SchemeItem::sourceId).containsExactly(one.sourceId(), two.sourceId(), three.sourceId());
        assertThat(preview).extracting(SchemeItem::weight)
                .containsExactly(new BigDecimal("33.3334"), new BigDecimal("33.3333"), new BigDecimal("33.3333"));
    }

    @Test
    void rejectsStaleVersionAndInvalidWeightsBeforeSavingRevision() {
        var gradebook = new GradebookEntity();
        gradebook.setVersion(1);
        when(gradebooks.findLockedByClassroomId(classroom)).thenReturn(Optional.of(gradebook));
        var item = new SchemeItem(ASSIGNMENT, UUID.randomUUID(), new BigDecimal("99.9999"), true);
        assertThatThrownBy(() -> service.apply(principal, classroom, new SchemeApplyRequest(WEIGHTED, 0L, List.of(item), null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> service.apply(principal, classroom, new SchemeApplyRequest(WEIGHTED, 1L, List.of(item), null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("100");
        verifyNoInteractions(revisions, items);
        assertThat(gradebook.getActiveSchemeRevisionId()).isNull();
    }
}
