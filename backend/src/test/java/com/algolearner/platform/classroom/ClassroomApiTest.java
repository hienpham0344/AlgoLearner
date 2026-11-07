package com.algolearner.platform.classroom;

import com.algolearner.platform.classroom.mock.InMemoryClassroomRepository;
import com.algolearner.platform.profile.mock.MockData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"logging.level.root=WARN","logging.level.org.springframework=WARN","debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles({"mock","test"})
class ClassroomApiTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class TimeConfiguration {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        Clock testClock() { return Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"),ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired InMemoryClassroomRepository repository;

    @BeforeEach void reset() { repository.reset(); }

    @Test void teacherCreatesClassWithoutExposingJoinCode() throws Exception {
        var result=authorized(post("/api/v1/classrooms").contentType("application/json")
                .content("{\"name\":\"Graph Theory\",\"joinMode\":\"AUTO_JOIN\"}"),"teacher")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.ownerAccountId").value(MockData.id("teacher").toString()))
                .andExpect(jsonPath("$.data.classCode").doesNotExist());
        assertThat(data(result).path("readOnly").asBoolean()).isFalse();
    }

    @Test void ownershipCheckBlocksIdor() throws Exception {
        authorized(patch("/api/v1/classrooms/"+MockData.id("class3")).contentType("application/json")
                .content("{\"name\":\"Stolen\"}"),"teacher").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
    }

    @Test void regenerateInvalidatesOldCodeAndNewCodeCanJoin() throws Exception {
        String classId=create("AUTO_JOIN");
        String oldCode=joinCode(classId);
        var regenerated=authorized(post("/api/v1/classrooms/"+classId+"/join-code/regenerate"),"teacher")
                .andExpect(status().isOk());
        String newCode=data(regenerated).path("classCode").asText();
        assertThat(newCode).isNotEqualTo(oldCode);
        authorized(post("/api/v1/classrooms/join").contentType("application/json")
                .content("{\"classCode\":\""+oldCode+"\"}"),"empty").andExpect(status().isConflict());
        authorized(post("/api/v1/classrooms/join").contentType("application/json")
                .content("{\"classCode\":\""+newCode+"\"}"),"empty").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("JOINED"));
    }

    @Test void approvalJoinCreatesRequestThenMembership() throws Exception {
        String classId=create("REQUIRE_APPROVAL"), code=joinCode(classId);
        var joined=authorized(post("/api/v1/classrooms/join").contentType("application/json")
                .content("{\"classCode\":\""+code+"\"}"),"empty").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("PENDING_APPROVAL"));
        String requestId=data(joined).path("joinRequestId").asText();
        authorized(post("/api/v1/classrooms/"+classId+"/join-requests/"+requestId+"/approve"),"teacher")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        authorized(get("/api/v1/classrooms/"+classId),"empty").andExpect(status().isOk());
    }

    @Test void invitationCanOnlyBeAcceptedByInvitee() throws Exception {
        String classId=create("AUTO_JOIN");
        var invited=authorized(post("/api/v1/classrooms/"+classId+"/invitations").contentType("application/json")
                .content("{\"email\":\"empty@example.test\",\"expiresAt\":\"2026-11-08T10:00:00Z\"}"),"teacher")
                .andExpect(status().isCreated());
        String invitationId=data(invited).path("id").asText();
        authorized(post("/api/v1/classrooms/invitations/"+invitationId+"/accept"),"student2")
                .andExpect(status().isForbidden());
        authorized(post("/api/v1/classrooms/invitations/"+invitationId+"/accept"),"empty")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test void classWithHistoryCannotBeDeletedButEmptyClassCan() throws Exception {
        authorized(delete("/api/v1/classrooms/"+MockData.id("class1")),"teacher").andExpect(status().isConflict());
        String empty=create("AUTO_JOIN");
        authorized(delete("/api/v1/classrooms/"+empty),"teacher").andExpect(status().isNoContent());
    }

    @Test void adminTransfersOwnershipWithoutChangingClassIdentity() throws Exception {
        String classId=create("AUTO_JOIN");
        authorized(post("/api/v1/admin/classrooms/"+classId+"/transfer-ownership").contentType("application/json")
                .content("{\"newOwnerAccountId\":\""+MockData.id("teacher2")+"\"}"),"admin")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(classId))
                .andExpect(jsonPath("$.data.ownerAccountId").value(MockData.id("teacher2").toString()));
    }

    private String create(String mode) throws Exception {
        var result=authorized(post("/api/v1/classrooms").contentType("application/json")
                .content("{\"name\":\"New Classroom\",\"joinMode\":\""+mode+"\"}"),"teacher")
                .andExpect(status().isCreated());
        return data(result).path("id").asText();
    }
    private String joinCode(String classId) throws Exception {
        return data(authorized(get("/api/v1/classrooms/"+classId+"/join-code"),"teacher")
                .andExpect(status().isOk())).path("classCode").asText();
    }
    private ResultActions authorized(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,String user) throws Exception {
        return mvc.perform(request.header("Authorization","Bearer mock-"+user));
    }
    private JsonNode data(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }
}
