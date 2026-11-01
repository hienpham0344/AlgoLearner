package com.algolearner.platform.classroom;

import com.algolearner.platform.auth.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classroom-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@AutoConfigureMockMvc
@Transactional
class ClassroomApiTest {
    @TestConfiguration
    static class TestConfig {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    private AccountEntity teacher;
    private AccountEntity otherTeacher;
    private AccountEntity student;
    private AccountEntity admin;

    @BeforeEach
    void setupAccounts() {
        teacher = account("teacher", AccountRole.TEACHER); otherTeacher = account("other", AccountRole.TEACHER);
        student = account("student", AccountRole.STUDENT);
        admin = account("admin", AccountRole.ADMIN);
    }

    @Test
    void teacherCreatesClassThroughApiWithEnvelope() throws Exception {
        mvc.perform(post("/api/v1/classrooms").with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algorithms\",\"description\":\"Core\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Algorithms")).andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.ownerId").value(teacher.getId().toString()))
                .andExpect(jsonPath("$.data.classCode").isString());
    }

    @Test
    void nonOwnerIsForbiddenFromChangingClass() throws Exception {
        String created = mvc.perform(post("/api/v1/classrooms").with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algorithms\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).path("data").path("id").asText();
        mvc.perform(patch("/api/v1/classrooms/{id}", id).with(actor(otherTeacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Attack\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
    }

    @Test
    void studentJoinsViaClassCode() throws Exception {
        String created = mvc.perform(post("/api/v1/classrooms").with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algorithms\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andReturn().getResponse().getContentAsString();
        String code = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).path("data").path("classCode").asText();
        mvc.perform(post("/api/v1/classrooms/join").with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.outcome").value("JOINED"));
    }

    @Test
    void studentCanReadOwnClassButNotItsJoinCode() throws Exception {
        var created = createClass(teacher, "AUTO_JOIN");
        String id = created.path("id").asText();
        mvc.perform(post("/api/v1/classrooms/join").with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + created.path("classCode").asText() + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/classrooms/{id}", id).with(actor(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.classCode").isEmpty());
    }

    @Test
    void missingAuthenticationUsesStandardUnauthorizedEnvelope() throws Exception {
        mvc.perform(post("/api/v1/classrooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algorithms\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_ACCESS"));
    }

    @Test
    void directAddThenStudentLeavesAndRejoinsThroughApi() throws Exception {
        var created = createClass(teacher, "AUTO_JOIN");
        String id = created.path("id").asText();
        String code = created.path("classCode").asText();
        mvc.perform(post("/api/v1/classrooms/{id}/members/direct", id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + student.getUsername() + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/classrooms/{id}/leave", id).with(actor(student))).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/classrooms/join").with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.outcome").value("JOINED"));
    }

    @Test
    void invitationAcceptsOnlyForTargetStudent() throws Exception {
        var created = createClass(teacher, "AUTO_JOIN");
        String id = created.path("id").asText();
        String invitation = mvc.perform(post("/api/v1/classrooms/{id}/invitations", id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + student.getEmail() + "\",\"expiresAt\":\"2026-11-02T00:00:00Z\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String invitationId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(invitation).path("data").asText();
        mvc.perform(post("/api/v1/classrooms/invitations/{id}/accept", invitationId).with(actor(student)))
                .andExpect(status().isNoContent());
    }

    @Test
    void ownerCanArchiveButCannotDeleteClassWithMembershipHistory() throws Exception {
        var created = createClass(teacher, "AUTO_JOIN");
        String id = created.path("id").asText(); String code = created.path("classCode").asText();
        mvc.perform(post("/api/v1/classrooms/join").with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"classCode\":\"" + code + "\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/classrooms/{id}/archive", id).with(actor(teacher))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/classrooms/{id}", id).with(actor(teacher)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLASS_HAS_RETAINED_DATA"));
    }

    @Test
    void lockedCodeAndRevokedInvitationCannotAdmitStudent() throws Exception {
        var created = createClass(teacher, "AUTO_JOIN"); String id = created.path("id").asText(); String code = created.path("classCode").asText();
        mvc.perform(patch("/api/v1/classrooms/{id}/join-configuration", id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codeLocked\":true,\"joiningOpen\":true,\"joinMode\":\"AUTO_JOIN\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/classrooms/join").with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("CLASS_CODE_LOCKED"));
        String invite = mvc.perform(post("/api/v1/classrooms/{id}/invitations", id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + student.getEmail() + "\",\"expiresAt\":\"2026-11-02T00:00:00Z\"}"))
                .andReturn().getResponse().getContentAsString();
        String inviteId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(invite).path("data").asText();
        mvc.perform(post("/api/v1/classrooms/{classroomId}/invitations/{invitationId}/revoke", id, inviteId).with(actor(teacher))).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/classrooms/invitations/{id}/accept", inviteId).with(actor(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVITATION_UNAVAILABLE"));
    }

    @Test
    void adminCanCreateForTeacherAndTransferOwner() throws Exception {
        var created = mvc.perform(post("/api/v1/classrooms").with(actor(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin created\",\"joinMode\":\"AUTO_JOIN\",\"ownerId\":\"" + teacher.getId() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).path("data").path("id").asText();
        mvc.perform(post("/api/v1/classrooms/{id}/transfer-owner", id).with(actor(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetTeacherId\":\"" + otherTeacher.getId() + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/classrooms/{id}", id).with(actor(otherTeacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ownerId").value(otherTeacher.getId().toString()));
    }

    @Test
    void requireApprovalCreatesRequestThenOwnerApprovesAndStudentListsClass() throws Exception {
        var created = createClass(teacher, "REQUIRE_APPROVAL"); String id = created.path("id").asText(); String code = created.path("classCode").asText();
        mvc.perform(post("/api/v1/classrooms/join").with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.outcome").value("PENDING_APPROVAL"));
        String requests = mvc.perform(get("/api/v1/classrooms/{id}/join-requests", id).with(actor(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].studentId").value(student.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        String requestId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(requests).path("data").get(0).path("id").asText();
        mvc.perform(post("/api/v1/classrooms/{classroomId}/join-requests/{requestId}/approve", id, requestId).with(actor(teacher)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/classrooms").with(actor(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id));
    }

    private com.fasterxml.jackson.databind.JsonNode createClass(AccountEntity actor, String joinMode) throws Exception {
        String response = mvc.perform(post("/api/v1/classrooms").with(actor(actor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algorithms\",\"joinMode\":\"" + joinMode + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("data");
    }

    private AccountEntity account(String username, AccountRole role) {
        var account = new AccountEntity(); account.setId(UUID.randomUUID()); account.setUsername(username + UUID.randomUUID());
        account.setEmail(username + UUID.randomUUID() + "@example.test"); account.setRole(role); account.setStatus(AccountStatus.ACTIVE);
        return accounts.save(account);
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor actor(AccountEntity account) {
        return authentication(new UsernamePasswordAuthenticationToken(account.getId().toString(), "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))));
    }
}
