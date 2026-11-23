package com.algolearner.platform.classroom;

import com.algolearner.platform.announcement.AnnouncementDtos;
import com.algolearner.platform.lesson.CompletionMode;
import com.algolearner.platform.lesson.LessonDtos;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AnnouncementLessonAuthorizationAcceptanceTest extends AuthorizationAcceptanceSupport {
    @Test
    void announcementCommentRouteIsUnavailableAndCannotChangeAnnouncement() throws Exception {
        var announcement = announcement();
        var before = row("tbl_announcement", announcement);
        call(HttpMethod.POST, announcementPath(announcement) + "/comments", owner, Map.of("content", "Valid comment text"))
                .andExpect(status().isNotFound());
        assertThat(row("tbl_announcement", announcement)).isEqualTo(before);
        assertThat(rows("tbl_announcement")).hasSize(1);
    }

    @Test
    void announcementRejectsMultipartAttachmentAndAttachmentMetadataWithoutMutation() throws Exception {
        var announcement = announcement();
        var before = row("tbl_announcement", announcement);
        http.perform(multipart(classPath() + "/announcements")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "Safe text".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("title", "Valid title").param("content", "Valid text").with(identity(owner)))
                .andExpect(status().isUnsupportedMediaType());
        call(HttpMethod.POST, classPath() + "/announcements", owner,
                Map.of("title", "Valid title", "content", "Valid text", "attachments", java.util.List.of("notes.txt")))
                .andExpect(status().isBadRequest());
        assertThat(row("tbl_announcement", announcement)).isEqualTo(before);
        assertThat(rows("tbl_announcement")).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"CREATE", "UPDATE"})
    void announcementRejectsFuturePublishingWithoutChangingExistingText(String operation) throws Exception {
        var announcement = announcement();
        var before = row("tbl_announcement", announcement);
        call(operation.equals("CREATE") ? HttpMethod.POST : HttpMethod.PUT,
                operation.equals("CREATE") ? classPath() + "/announcements" : announcementPath(announcement), owner,
                Map.of("title", "Valid future title", "content", "Valid future text", "publishAt", "2100-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        assertThat(row("tbl_announcement", announcement)).isEqualTo(before);
        assertThat(rows("tbl_announcement")).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"CREATE", "UPDATE"})
    void invalidAnnouncementCreateAndUpdateLeaveSavedAnnouncementUnchanged(String operation) throws Exception {
        var announcement = announcement();
        var before = row("tbl_announcement", announcement);
        call(operation.equals("CREATE") ? HttpMethod.POST : HttpMethod.PUT,
                operation.equals("CREATE") ? classPath() + "/announcements" : announcementPath(announcement), owner,
                Map.of("title", " ", "content", "Valid changed content"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        assertThat(row("tbl_announcement", announcement)).isEqualTo(before);
        assertThat(rows("tbl_announcement")).hasSize(1);
    }

    static Stream<Arguments> pinMutations() {
        return Stream.of(Arguments.of("STUDENT", "pin"), Arguments.of("OTHER_TEACHER", "pin"),
                Arguments.of("STUDENT", "unpin"), Arguments.of("OTHER_TEACHER", "unpin"));
    }

    @ParameterizedTest @MethodSource("pinMutations")
    void studentAndNonOwnerCannotPinOrUnpinAndStoredStateDoesNotChange(String actor, String action) throws Exception {
        addStudent(student);
        var announcement = announcement();
        if (action.equals("unpin")) announcements.pin(owner.getId(), classroom.id(), announcement);
        var before = row("tbl_announcement", announcement);
        call(HttpMethod.PATCH, announcementPath(announcement) + "/" + action, actor(actor), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
        assertThat(row("tbl_announcement", announcement)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"CREATE", "UPDATE", "PROGRESS"})
    void otherTeacherCannotCreateUpdateLessonOrReadStudentProgress(String action) throws Exception {
        addStudent(student);
        UUID lesson = lesson();
        lessons.publish(owner.getId(), classroom.id(), lesson);
        lessons.completeLesson(student.getId(), classroom.id(), lesson);
        var before = row("tbl_lesson", lesson);
        var progress = jdbc.queryForList("SELECT * FROM tbl_lesson_completion WHERE lesson_id=? ORDER BY id", lesson);
        String path = action.equals("CREATE") ? classPath() + "/lessons"
                : lessonPath(lesson) + (action.equals("PROGRESS") ? "/progress" : "");
        HttpMethod method = action.equals("CREATE") ? HttpMethod.POST : action.equals("UPDATE") ? HttpMethod.PUT : HttpMethod.GET;
        call(method, path, otherTeacher, action.equals("PROGRESS") ? null : lessonInput())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
        assertThat(row("tbl_lesson", lesson)).isEqualTo(before);
        assertThat(rows("tbl_lesson")).hasSize(1);
        assertThat(jdbc.queryForList("SELECT * FROM tbl_lesson_completion WHERE lesson_id=? ORDER BY id", lesson)).isEqualTo(progress);
    }

    @ParameterizedTest @ValueSource(strings = {"CREATE", "UPDATE"})
    void lessonRejectsFuturePublishingWithoutChangingSavedState(String operation) throws Exception {
        UUID lesson = lesson();
        var before = row("tbl_lesson", lesson);
        var body = new java.util.LinkedHashMap<String, Object>(lessonInput());
        body.put("publishAt", "2100-01-01T00:00:00Z");
        call(operation.equals("CREATE") ? HttpMethod.POST : HttpMethod.PUT,
                operation.equals("CREATE") ? classPath() + "/lessons" : lessonPath(lesson), owner, body)
                .andExpect(status().isBadRequest());
        assertThat(row("tbl_lesson", lesson)).isEqualTo(before);
        assertThat(rows("tbl_lesson")).hasSize(1);
    }

    static Stream<Arguments> lessonMutations() {
        return Stream.of("STUDENT", "OTHER_TEACHER").flatMap(actor ->
                Stream.of("publish", "unpublish", "archive", "restore", "delete").map(action -> Arguments.of(actor, action)));
    }

    @ParameterizedTest @MethodSource("lessonMutations")
    void studentAndNonOwnerCannotChangeValidLessonStateArchiveRestoreOrDelete(String actor, String action) throws Exception {
        addStudent(student);
        UUID lesson = lesson();
        if (action.equals("unpublish") || action.equals("archive") || action.equals("restore"))
            lessons.publish(owner.getId(), classroom.id(), lesson);
        if (action.equals("restore")) lessons.archive(owner.getId(), classroom.id(), lesson);
        var before = row("tbl_lesson", lesson);
        call(action.equals("delete") ? HttpMethod.DELETE : HttpMethod.PATCH,
                lessonPath(lesson) + (action.equals("delete") ? "" : "/" + action), actor(actor), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
        assertThat(row("tbl_lesson", lesson)).isEqualTo(before);
        assertThat(rows("tbl_lesson")).hasSize(1);
    }

    private UUID announcement() {
        return announcements.create(owner.getId(), classroom.id(), new AnnouncementDtos.CreateRequest("Original announcement", "Saved text")).id();
    }
    private UUID lesson() {
        return lessons.create(owner.getId(), classroom.id(), new LessonDtos.CreateLessonRequest("Original Lesson", "Saved content", null, null, CompletionMode.MANUAL)).id();
    }
    private String announcementPath(UUID id) { return classPath() + "/announcements/" + id; }
    private String lessonPath(UUID id) { return classPath() + "/lessons/" + id; }
    private Map<String, Object> lessonInput() { return Map.of("title", "Valid changed Lesson", "content", "Valid changed content", "completionMode", "MANUAL"); }
}
