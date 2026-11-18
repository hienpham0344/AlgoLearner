package com.algolearner.platform.lesson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LessonControllerTest {

    private final LessonService service = mock(LessonService.class);
    private LessonController controller;

    private Authentication auth;
    private UUID actorId;
    private UUID classId;

    @BeforeEach
    void setUp() {
        controller = new LessonController(service);
        actorId = UUID.randomUUID();
        classId = UUID.randomUUID();
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(actorId.toString());
    }

    @Test
    void listLessons_ReturnsListInApiResponse() {
        var summary = new LessonDtos.LessonSummaryView(
                UUID.randomUUID(), classId, "Title", LessonStatus.PUBLISHED, CompletionMode.MANUAL, 0, false, null, Instant.now(), Instant.now()
        );
        when(service.list(actorId, classId)).thenReturn(List.of(summary));

        var response = controller.list(auth, classId);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
    }

    @Test
    void publishAndArchive_ReturnDetailViews() {
        var lessonId = UUID.randomUUID();
        var published = new LessonDtos.LessonDetailView(
                lessonId, classId, "Title", "Content", "Code", "[]", LessonStatus.PUBLISHED, CompletionMode.MANUAL, 0, List.of(), List.of(), false, null, Instant.now(), Instant.now()
        );
        when(service.publish(actorId, classId, lessonId)).thenReturn(published);

        var pubRes = controller.publish(auth, classId, lessonId);
        assertThat(pubRes.isSuccess()).isTrue();
        assertThat(pubRes.getData().status()).isEqualTo(LessonStatus.PUBLISHED);

        var archived = new LessonDtos.LessonDetailView(
                lessonId, classId, "Title", "Content", "Code", "[]", LessonStatus.ARCHIVED, CompletionMode.MANUAL, 0, List.of(), List.of(), false, null, Instant.now(), Instant.now()
        );
        when(service.archive(actorId, classId, lessonId)).thenReturn(archived);

        var archRes = controller.archive(auth, classId, lessonId);
        assertThat(archRes.isSuccess()).isTrue();
        assertThat(archRes.getData().status()).isEqualTo(LessonStatus.ARCHIVED);

        when(service.unpublish(actorId, classId, lessonId)).thenReturn(published);
        var unpubRes = controller.unpublish(auth, classId, lessonId);
        assertThat(unpubRes.isSuccess()).isTrue();

        when(service.restore(actorId, classId, lessonId)).thenReturn(published);
        var restoreRes = controller.restore(auth, classId, lessonId);
        assertThat(restoreRes.isSuccess()).isTrue();
    }

    @Test
    void crudAndMaterialsAndLinks_EndpointsWork() {
        var lessonId = UUID.randomUUID();
        var detail = new LessonDtos.LessonDetailView(
                lessonId, classId, "Title", "Content", "Code", "[]", LessonStatus.PUBLISHED, CompletionMode.MANUAL, 0, List.of(), List.of(), false, null, Instant.now(), Instant.now()
        );

        var createReq = new LessonDtos.CreateLessonRequest("Title", "Content", "Code", "[]", CompletionMode.MANUAL);
        when(service.create(actorId, classId, createReq)).thenReturn(detail);
        var createRes = controller.create(auth, classId, createReq);
        assertThat(createRes.isSuccess()).isTrue();

        when(service.get(actorId, classId, lessonId)).thenReturn(detail);
        var getRes = controller.get(auth, classId, lessonId);
        assertThat(getRes.isSuccess()).isTrue();

        var updateReq = new LessonDtos.UpdateLessonRequest("Title 2", "Content 2", "Code 2", "[]", CompletionMode.MANUAL);
        when(service.update(actorId, classId, lessonId, updateReq)).thenReturn(detail);
        var updateRes = controller.update(auth, classId, lessonId, updateReq);
        assertThat(updateRes.isSuccess()).isTrue();

        controller.delete(auth, classId, lessonId);
        verify(service).delete(actorId, classId, lessonId);

        var matReq = new LessonDtos.AddMaterialRequest("file.pdf", "http://url", MaterialFileType.PDF, 100L);
        var matView = new LessonDtos.MaterialView(UUID.randomUUID(), "file.pdf", "http://url", MaterialFileType.PDF, 100L, Instant.now());
        when(service.addMaterial(actorId, classId, lessonId, matReq)).thenReturn(matView);
        var matRes = controller.addMaterial(auth, classId, lessonId, matReq);
        assertThat(matRes.isSuccess()).isTrue();

        var linkReq = new LessonDtos.LinkItemRequest(LinkedItemType.ASSIGNMENT, UUID.randomUUID());
        var linkView = new LessonDtos.LinkedItemView(UUID.randomUUID(), LinkedItemType.ASSIGNMENT, linkReq.itemId(), 0);
        when(service.linkItem(actorId, classId, lessonId, linkReq)).thenReturn(linkView);
        var linkRes = controller.linkItem(auth, classId, lessonId, linkReq);
        assertThat(linkRes.isSuccess()).isTrue();

        controller.unlinkItem(auth, classId, lessonId, linkReq.itemId());
        verify(service).unlinkItem(actorId, classId, lessonId, linkReq.itemId());

        var materialId = UUID.randomUUID();
        controller.removeMaterial(auth, classId, lessonId, materialId);
        verify(service).removeMaterial(actorId, classId, lessonId, materialId);

        var caps = new LessonDtos.CapabilitiesView(true, true, 1024L, List.of("pdf"));
        when(service.capabilities(actorId, classId)).thenReturn(caps);
        var capsRes = controller.capabilities(auth, classId);
        assertThat(capsRes.isSuccess()).isTrue();
        assertThat(capsRes.getData()).isEqualTo(caps);

        var activity = new LessonDtos.ActivityOptionView(UUID.randomUUID(), LinkedItemType.ASSIGNMENT, "Lab 1", lessonId, true);
        when(service.activities(actorId, classId)).thenReturn(List.of(activity));
        var actRes = controller.activities(auth, classId);
        assertThat(actRes.isSuccess()).isTrue();
        assertThat(actRes.getData()).containsExactly(activity);

        controller.complete(auth, classId, lessonId);
        verify(service).completeLesson(actorId, classId, lessonId);
    }
}
