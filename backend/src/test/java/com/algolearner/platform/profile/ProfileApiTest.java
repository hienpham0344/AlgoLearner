package com.algolearner.platform.profile;

import com.algolearner.platform.profile.mock.MockData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"logging.level.root=WARN","logging.level.org.springframework=WARN","debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class ProfileApiTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class TimeConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        java.time.Clock testClock() {
            return java.time.Clock.fixed(java.time.Instant.parse("2026-10-29T10:00:00Z"),java.time.ZoneOffset.UTC);
        }
    }
    @TempDir static Path avatars;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("app.storage.avatar-directory", () -> avatars.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MockData data;
    @Autowired java.time.Clock clock;

    @BeforeEach void resetProfiles() {
        // Each test restores mutable profile fixtures; no ordering dependency.
        var fresh = new MockData(clock);
        data.profiles.clear();
        data.profiles.putAll(fresh.profiles);
    }
    ResultActions read(String path, String user) throws Exception {
        var request = get("/api/v1" + path);
        if (user != null) request.header("Authorization", "Bearer mock-" + user);
        return mvc.perform(request);
    }
    ResultActions patchProfile(String suffix, String body) throws Exception {
        return mvc.perform(patch("/api/v1/users/me/profile" + suffix)
                .header("Authorization", "Bearer mock-student").contentType("application/json").content(body));
    }
    JsonNode body(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }

    @ParameterizedTest(name="TC-PRO-01-01: {0}")
    @ValueSource(strings={"student", "teacher"})
    void ownProfile(String user) throws Exception {
        read("/users/me/profile", user).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(user))
                .andExpect(jsonPath("$.data.email").value(user + "@example.test"))
                .andExpect(jsonPath("$.data.fullName").value("Demo " + user))
                .andExpect(jsonPath("$.data.role").value(user.toUpperCase()))
                .andExpect(jsonPath("$.data.joinedAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.data.publicPracticeStatistics.solved").isNumber())
                .andExpect(jsonPath("$.data.streak.currentStreak").isNumber());
    }
    @Test @DisplayName("TC-PRO-01-02: update text and replace avatar")
    void updateProfileAndAvatar() throws Exception {
        patchProfile("", "{\"fullName\":\"  Nguyen An  \",\"shortBio\":\"Algorithms\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Nguyen An"));
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB), "png", bytes);
        mvc.perform(multipart("/api/v1/users/me/profile/avatar")
                .file(new MockMultipartFile("file", "avatar.png", "image/png", bytes.toByteArray()))
                .with(request -> { request.setMethod("PUT"); return request; })
                .header("Authorization", "Bearer mock-student"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").isString());
        read("/users/student/profile/avatar", null).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        read("/users/me/profile", "student").andExpect(jsonPath("$.data.shortBio").value("Algorithms"));
    }
    @ParameterizedTest(name="TC-PRO-01-03: immutable {0}")
    @ValueSource(strings={"username", "email", "role", "accountId"})
    void rejectsImmutableFields(String field) throws Exception {
        patchProfile("", "{\"fullName\":\"Changed\",\"" + field + "\":\"attacker\"}").andExpect(status().isBadRequest());
        read("/users/me/profile", "student").andExpect(jsonPath("$.data.fullName").value("Demo student"))
                .andExpect(jsonPath("$.data.username").value("student"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
    }
    @Test @DisplayName("TC-PRO-01-04: invalid avatar preserves old key")
    void invalidAvatar() throws Exception {
        var old = data.profiles.get(MockData.id("student"));
        mvc.perform(multipart("/api/v1/users/me/profile/avatar")
                .file(new MockMultipartFile("file","fake.png","image/png",new byte[]{1,2,3}))
                .with(request -> { request.setMethod("PUT"); return request; })
                .header("Authorization","Bearer mock-student"))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(data.profiles.get(MockData.id("student"))).isEqualTo(old);
    }
    @Test @DisplayName("TC-PRO-02-01: privacy toggle and exact safe data allowlist")
    void publicPrivacy() throws Exception {
        patchProfile("/privacy", "{\"isPublic\":true}").andExpect(status().isOk());
        var profile = body(read("/users/student/profile",null).andExpect(status().isOk()));
        Set<String> fields = new HashSet<>();
        profile.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("username","avatarUrl","shortBio","solved","streak");
        patchProfile("/privacy", "{\"isPublic\":false}").andExpect(status().isOk());
        read("/users/student/profile",null).andExpect(status().isNotFound());
        read("/users/me/profile","student").andExpect(status().isOk());
    }
    @ParameterizedTest(name="TC-PRO-02-02: hidden {0}")
    @ValueSource(strings={"suspended","deactivated","student2","unknown"})
    void hidesUnavailableProfiles(String user) throws Exception {
        read("/users/"+user+"/profile",null).andExpect(status().isNotFound());
        read("/users/"+user+"/profile/avatar",null).andExpect(status().isNotFound());
    }
    @Test @DisplayName("TC-PRO-03-01/02: student summary and account scope")
    void studentDashboard() throws Exception {
        var first=body(read("/dashboards/student?accountId="+MockData.id("student2"),"student").andExpect(status().isOk()));
        assertThat(first.path("classrooms").path("items").size()).isEqualTo(2);
        assertThat(first.path("publicPracticeStatistics").path("solved").asInt()).isEqualTo(2);
        for(String section:Set.of("upcomingAssignments","upcomingQuizzes","enrolledCourses","recentNotifications"))
            assertThat(first.path(section).path("items").isArray()).isTrue();
        var second=body(read("/dashboards/student","student2").andExpect(status().isOk()));
        Set<String> secondClasses=new HashSet<>();
        second.path("classrooms").path("items").forEach(x -> secondClasses.add(x.path("classroomId").asText()));
        first.path("classrooms").path("items").forEach(x -> assertThat(secondClasses).doesNotContain(x.path("classroomId").asText()));
        assertThat(first.path("generatedAt").asText()).endsWith("Z");
        assertThat(first.path("displayTimeZone").asText()).isEqualTo("Asia/Ho_Chi_Minh");
    }
    @Test @DisplayName("TC-PRO-04-01/02: teacher ownership across dashboard sections")
    void teacherDashboard() throws Exception {
        var dashboard=body(read("/dashboards/teacher?teacherId="+MockData.id("teacher2"),"teacher").andExpect(status().isOk()));
        for(String section:Set.of("classrooms","openOrUpcomingAssignments","ungradedSubmissions","recentSubmissions","classroomStatistics")) {
            var items=dashboard.path(section).path("items");
            assertThat(items.isArray()).isTrue();
            items.forEach(x -> assertThat(x.path("classroomId").asText()).isIn(MockData.id("class1").toString(),MockData.id("class2").toString()));
        }
        assertThat(dashboard.has("pendingContributions")).isTrue();
        assertThat(dashboard.has("recentNotifications")).isTrue();
    }
    @Test @DisplayName("TC-PRO-05-01: admin summary")
    void adminDashboard() throws Exception {
        var dashboard=body(read("/admin/dashboard","admin").andExpect(status().isOk()));
        for(String section:Set.of("totals","pendingTeacherContributions","accountModeration","recentActivities","importantAuditEvents","configurationShortcuts","charts"))
            assertThat(dashboard.has(section)).as(section).isTrue();
        assertThat(dashboard.path("totals").path("teacherCount").asInt()).isEqualTo(2);
        assertThat(dashboard.path("charts").path("items").size()).isPositive();
    }
    @ParameterizedTest(name="TC-PRO-05-02: denied {0}")
    @ValueSource(strings={"student","teacher"})
    void adminRoleDenied(String user) throws Exception {
        read("/admin/dashboard",user).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
    }
    @ParameterizedTest @ValueSource(strings={"suspended","deactivated","locked","unknown"})
    void inactiveAuthentication(String user) throws Exception {
        read("/users/me/profile",user).andExpect(status().isUnauthorized());
    }
    @Test void missingTokenAndCrossRole() throws Exception {
        read("/users/me/profile",null).andExpect(status().isUnauthorized());
        read("/dashboards/student","teacher").andExpect(status().isForbidden());
        read("/dashboards/teacher","student").andExpect(status().isForbidden());
        read("/users/me/profile","admin").andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"fullName\":123}","{\"isPublic\":null}"})
    void rejectsInvalidBodies(String json) throws Exception {
        patchProfile(json.contains("isPublic")?"/privacy":"",json).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"previewSize=0","previewSize=21","upcomingDays=0","upcomingDays=31","previewSize=abc"})
    void invalidDashboardBounds(String query) throws Exception {
        read("/dashboards/student?"+query,"student").andExpect(status().isBadRequest());
    }
    @Test void emptyDashboard() throws Exception {
        read("/dashboards/student","empty").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classrooms.totalElements").value(0))
                .andExpect(jsonPath("$.data.publicPracticeStatistics.solved").value(0));
    }
}
