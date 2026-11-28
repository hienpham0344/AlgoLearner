package com.algolearner.platform.problem;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.storage.MalwareScanner;
import com.algolearner.platform.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties={"logging.level.root=WARN","app.security.jwt-secret=public-practice-test-only-key-at-least-32-bytes","app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth","test"})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
class ProblemApiTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired ObjectMapper json;
    @MockitoBean MalwareScanner scanner;
    @MockitoBean com.algolearner.platform.storage.PrivateObjectStorage objects;

    @Test
    void reviewPublicationDiscoveryAndScreenshotReportUseAuthenticatedRoutes() throws Exception {
        var teacher=actor(AccountRole.TEACHER);
        var student=actor(AccountRole.STUDENT);
        var admin=actor(AccountRole.ADMIN);
        String content="""
                {"title":"Echo","statement":"Repeat the input","languages":["PYTHON"],"difficulty":"MEDIUM",
                 "tags":["strings"],"editorial":"Return the input.","testCases":[{"input":"a","expectedOutput":"a","sample":true}],
                 "limits":{"compileSeconds":2,"runtimeMillis":1000,"outputBytes":1024},"feedback":"PASS_FAIL"}
                """;
        String created=mvc.perform(post("/api/v1/problems").with(teacher).contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(created).get("data").asText();
        String problem="/api/v1/problems/"+id;
        mvc.perform(post(problem+"/submit-review").with(teacher)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/problems/"+id+"/review").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}")).andExpect(status().isOk());
        mvc.perform(get(problem).with(student)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/admin/problems/"+id+"/publication").with(admin).param("status","PUBLISHED")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/problems").with(student).param("tag","strings").param("difficulty","MEDIUM")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].title").value("Echo"));
        mvc.perform(get(problem+"/editorial").with(student)).andExpect(status().isOk()).andExpect(jsonPath("$.data").value("Return the input."));
        var buffer=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",buffer);
        var image=new MockMultipartFile("image","issue.png","image/png",buffer.toByteArray());
        org.mockito.Mockito.doAnswer(invocation -> {
            ((java.io.OutputStream) invocation.getArgument(1)).write(buffer.toByteArray());
            return null;
        }).when(objects).download(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
        var report=new MockMultipartFile("report","","application/json","{\"reason\":\"OTHER\",\"description\":\"Typo\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String response=mvc.perform(multipart(problem+"/reports").file(report).file(image).with(student)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String imageUrl="/api/v1/admin/problems/reports/"+json.readTree(response).get("data").asText()+"/image";
        mvc.perform(get(imageUrl).with(admin)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Security-Policy","sandbox; default-src 'none'"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Disposition","attachment; filename=\"report-image\""))
                .andExpect(content().bytes(buffer.toByteArray()));
        mvc.perform(get("/api/v1/problems").with(student).param("q","x".repeat(256))).andExpect(status().isBadRequest());
        mvc.perform(multipart(problem+"/reports").file(report).with(student)).andExpect(status().isConflict());
        mvc.perform(get(imageUrl).with(student)).andExpect(status().isForbidden());
    }

    private RequestPostProcessor actor(AccountRole role) {
        var account=new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("p-"+account.getId().toString().substring(0,12));
        account.setEmail(account.getUsername()+"@example.test");
        account.setFullName(role.name());
        account.setPasswordHash("test-fixture");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        accounts.saveAndFlush(account);
        return authentication(new UsernamePasswordAuthenticationToken(account.getId().toString(),"",List.of(new SimpleGrantedAuthority("ROLE_"+role.name()))));
    }
}
