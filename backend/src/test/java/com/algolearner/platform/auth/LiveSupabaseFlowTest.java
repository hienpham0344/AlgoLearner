package com.algolearner.platform.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in HTTP/JPA smoke test against the configured Supabase DB. Leaves identifiable records. */
@SpringBootTest(properties = "app.mail.from=codex-integration@example.test")
@AutoConfigureMockMvc
@ActiveProfiles("internal-auth")
@EnabledIfSystemProperty(named = "live.supabase.check", matches = "true")
class LiveSupabaseFlowTest {
    private static final String PASSWORD = "A1" + SecretTokens.create();
    private static final Pattern CODE = Pattern.compile("\\b[0-9]{6}\\b");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean JavaMailSender sender;

    @Test
    void registrationSessionsProfileAndInvitationPersistThroughRealApi() throws Exception {
        var deliveredCodes = new ConcurrentHashMap<String, String>();
        doAnswer(invocation -> {
            SimpleMailMessage mail = invocation.getArgument(0);
            var matcher = CODE.matcher(mail.getText());
            assertThat(matcher.find()).isTrue();
            deliveredCodes.put(mail.getTo()[0], matcher.group());
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));

        String marker = "codexlive" + Long.toString(Instant.now().getEpochSecond(), 36);
        String teacherEmail = marker + "t@example.test";
        String studentEmail = marker + "s@example.test";
        String teacherToken = registerAndVerify(marker + "t", teacherEmail, "TEACHER", deliveredCodes);
        String studentToken = registerAndVerify(marker + "s", studentEmail, "STUDENT", deliveredCodes);

        mvc.perform(get("/api/v1/users/me/profile").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
        JsonNode sessions = data(get("/api/v1/auth/sessions"), studentToken);
        assertThat(sessions.isArray()).isTrue();
        assertThat(sessions.size()).isEqualTo(1);
        assertThat(sessions.get(0).path("current").asBoolean()).isTrue();

        JsonNode classroom = data(post("/api/v1/classrooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", marker + " classroom", "joinMode", "AUTO_JOIN"))),
                teacherToken);
        String classroomId = classroom.path("id").asText();
        assertThat(classroom.path("classCode").asText()).isNotBlank();

        mvc.perform(get("/api/v1/classrooms/{id}", classroomId)
                .header("Authorization", bearer(studentToken))).andExpect(status().isForbidden());

        JsonNode invitation = data(post("/api/v1/classrooms/{id}/invitations", classroomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", studentEmail,
                        "expiresAt", Instant.now().plusSeconds(3600).toString()))), teacherToken);
        String invitationId = invitation.asText();
        JsonNode notifications = data(get("/api/v1/notifications"), studentToken).path("items");
        assertThat(notifications.size()).isEqualTo(1);
        assertThat(notifications.get(0).path("invitationId").asText()).isEqualTo(invitationId);

        mvc.perform(post("/api/v1/classrooms/invitations/{id}/accept", invitationId)
                .header("Authorization", bearer(studentToken))).andExpect(status().isNoContent());
        assertThat(data(get("/api/v1/classrooms"), studentToken).path("items").size()).isEqualTo(1);
        assertThat(data(get("/api/v1/notifications"), studentToken).path("items").get(0)
                .path("status").asText()).isEqualTo("ACCEPTED");
        mvc.perform(post("/api/v1/auth/logout-all").header("Authorization", bearer(studentToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me/profile").header("Authorization", bearer(studentToken)))
                .andExpect(status().isUnauthorized());
        String freshStudentToken = data(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("usernameOrEmail", studentEmail,
                        "password", PASSWORD, "rememberMe", false))), null).path("token").asText();
        deactivate(freshStudentToken);
        deactivate(teacherToken);
        System.out.println("Live Supabase test marker: " + marker);
    }

    private String registerAndVerify(String username, String email, String role,
            Map<String, String> deliveredCodes) throws Exception {
        var request = Map.of("email", email, "username", username, "password", PASSWORD,
                "confirmPassword", PASSWORD, "role", role, "termsAccepted", true,
                "privacyAccepted", true);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isCreated());
        String code = deliveredCodes.get(email);
        assertThat(code).isNotBlank();
        return data(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "code", code,
                        "purpose", "REGISTER"))), null).path("token").asText();
    }

    private JsonNode data(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token) throws Exception {
        if (token != null) request.header("Authorization", bearer(token));
        String body = mvc.perform(request).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data");
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private void deactivate(String token) throws Exception {
        mvc.perform(post("/api/v1/auth/deactivate").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "confirmed", true))))
                .andExpect(status().isNoContent());
    }
}
