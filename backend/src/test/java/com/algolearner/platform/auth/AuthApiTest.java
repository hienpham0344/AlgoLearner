package com.algolearner.platform.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.security.jwt-secret=auth-api-test-secret-unique-and-long-enough-123",
        "app.mail.from=sender@example.test"
})
@AutoConfigureMockMvc
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AuthApiTest extends com.algolearner.platform.support.PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @MockBean JavaMailSender sender;

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "abcdefgh"})
    void registrationExplainsPasswordCompositionBeforeSendingMail(String password) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("invalid@example.test", "invaliduser")
                                .replace("ExamplePass123!", password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("Use at least one letter and one number."))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
        verifyNoInteractions(sender);
        org.junit.jupiter.api.Assertions.assertFalse(accounts.existsByEmailIgnoreCase("invalid@example.test"));
    }

    @Test
    void registrationExplainsPasswordByteLimitBeforeSendingMail() throws Exception {
        String password = "a1" + "é".repeat(36);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("long@example.test", "longuser")
                                .replace("ExamplePass123!", password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password is too long. Use at most 72 UTF-8 bytes."));
        verifyNoInteractions(sender);
        org.junit.jupiter.api.Assertions.assertFalse(accounts.existsByEmailIgnoreCase("long@example.test"));
    }

    @Test
    void registrationSendsOtpAndVerificationActivatesAccount() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("new@example.test", "newuser")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("new@example.test"));
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        mvc.perform(post("/api/v1/auth/resend-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.test\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isTooManyRequests());
        String text = message.getValue().getText();
        var matcher = Pattern.compile("\\b[0-9]{6}\\b").matcher(text);
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find());
        String code = matcher.group();
        String verifyBody = "{\"email\":\"new@example.test\",\"code\":\"" + code
                + "\",\"purpose\":\"REGISTER\"}";
        mvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.user.lifecycleStatus").value("ACTIVE"));
        mvc.perform(post("/api/v1/auth/verify-otp").contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isBadRequest());
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"newuser\",\"password\":\"ExamplePass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn().getResponse().getContentAsString();
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(login).path("data").path("token").asText();
        mvc.perform(get("/api/v1/users/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("newuser"));
        for (int attempt = 0; attempt < 4; attempt++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"usernameOrEmail\":\"newuser\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"newuser\",\"password\":\"wrong-password\"}"))
                .andExpect(status().is(423));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"newuser\",\"password\":\"ExamplePass123!\"}"))
                .andExpect(status().is(423));
    }

    @Test
    void failedDeliveryDoesNotCreateAccount() throws Exception {
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("retry@example.test", "retryuser")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("MAIL_DELIVERY_FAILED"));
        org.junit.jupiter.api.Assertions.assertFalse(accounts.existsByEmailIgnoreCase("retry@example.test"));
    }

    private static String registerBody(String email, String username) {
        return "{\"email\":\"" + email + "\",\"username\":\"" + username
                + "\",\"password\":\"ExamplePass123!\",\"confirmPassword\":\"ExamplePass123!\","
                + "\"role\":\"STUDENT\",\"termsAccepted\":true,\"privacyAccepted\":true}";
    }
}
