package com.algolearner.platform.announcement;

import com.algolearner.platform.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnnouncementControllerTest {

    private final AnnouncementService service = mock(AnnouncementService.class);
    private AnnouncementController controller;

    private Authentication auth;
    private UUID actorId;
    private UUID classId;

    @BeforeEach
    void setUp() {
        controller = new AnnouncementController(service);
        actorId = UUID.randomUUID();
        classId = UUID.randomUUID();
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(actorId.toString());
    }

    @Test
    void listAnnouncements_DelegatesToService() {
        var pageable = PageRequest.of(0, 20);
        var view = new AnnouncementDtos.AnnouncementView(
                UUID.randomUUID(), classId, actorId, "Teacher", "Title", "Content", false, null, Instant.now(), Instant.now()
        );
        when(service.list(actorId, classId, pageable))
                .thenReturn(PageResponse.fromPage(new PageImpl<>(List.of(view))));

        var result = controller.list(auth, classId, pageable);
        assertThat(result.getData().getItems()).hasSize(1);
        assertThat(result.getData().getItems().get(0).title()).isEqualTo("Title");
    }

    @Test
    void createAnnouncement_ReturnsApiResponse() {
        var request = new AnnouncementDtos.CreateRequest("Title", "Content");
        var view = new AnnouncementDtos.AnnouncementView(
                UUID.randomUUID(), classId, actorId, "Teacher", "Title", "Content", false, null, Instant.now(), Instant.now()
        );
        when(service.create(actorId, classId, request)).thenReturn(view);

        var response = controller.create(auth, classId, request);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().title()).isEqualTo("Title");
    }

    @Test
    void pinAndUnpin_ReturnApiResponse() {
        var announcementId = UUID.randomUUID();
        var pinnedView = new AnnouncementDtos.AnnouncementView(
                announcementId, classId, actorId, "Teacher", "Title", "Content", true, Instant.now(), Instant.now(), Instant.now()
        );
        when(service.pin(actorId, classId, announcementId)).thenReturn(pinnedView);

        var pinRes = controller.pin(auth, classId, announcementId);
        assertThat(pinRes.isSuccess()).isTrue();
        assertThat(pinRes.getData().isPinned()).isTrue();

        var unpinnedView = new AnnouncementDtos.AnnouncementView(
                announcementId, classId, actorId, "Teacher", "Title", "Content", false, null, Instant.now(), Instant.now()
        );
        when(service.unpin(actorId, classId, announcementId)).thenReturn(unpinnedView);

        var unpinRes = controller.unpin(auth, classId, announcementId);
        assertThat(unpinRes.isSuccess()).isTrue();
        assertThat(unpinRes.getData().isPinned()).isFalse();
    }
}
