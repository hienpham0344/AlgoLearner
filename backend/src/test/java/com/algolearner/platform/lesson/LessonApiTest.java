package com.algolearner.platform.lesson;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.security.AccountRepository;
import com.algolearner.platform.profile.PracticeRepository;
import com.algolearner.platform.profile.ProfileRepository;
import com.algolearner.platform.profile.dashboard.DashboardRepository;
import com.algolearner.platform.profile.dashboard.DashboardResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false",
        "app.security.jwt.issuer=https://issuer.example.test", "app.security.jwt.audience=classroom-tests",
        "app.security.jwt.jwk-set-uri=https://issuer.example.test/.well-known/jwks.json"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
public class LessonApiTest extends com.algolearner.platform.support.PostgresIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC); }
        @Bean @Primary TestAccounts testAccounts() { return new TestAccounts(); }
        @Bean @Primary DashboardRepository dashboardRepository() {
            return org.mockito.Mockito.mock(DashboardRepository.class, invocation -> {
                Class<?> type = invocation.getMethod().getReturnType();
                if (type == DashboardResponse.Preview.class) return new DashboardResponse.Preview<>(List.of(), 0, false);
                if (type == DashboardResponse.Contributions.class) return new DashboardResponse.Contributions(0, 0);
                if (type == DashboardResponse.Totals.class) return new DashboardResponse.Totals(0, 0, 0, 0, 0, 0);
                if (type == DashboardResponse.Moderation.class) return new DashboardResponse.Moderation(0, 0, 0);
                if (List.class.isAssignableFrom(type)) return List.of();
                return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
            });
        }
        @Bean @Primary PracticeRepository practiceRepository() { return accountId -> List.of(); }
        @Bean @Primary ProfileRepository profileRepository() { return org.mockito.Mockito.mock(ProfileRepository.class); }
    }

    static class TestAccounts implements AccountRepository {
        private final List<Account> records = new ArrayList<>();
        @Override public List<Account> accounts() { return List.copyOf(records); }
        @Override public List<Restriction> restrictions() { return List.of(); }
        void add(AccountEntity account) {
            records.add(new Account(account.getId(), account.getUsername(), account.getRole().name(),
                    account.getStatus().name(), account.getCreatedAt()));
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private com.algolearner.platform.auth.AccountRepository accounts;
    @Autowired private TestAccounts testAccounts;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.algolearner.platform.storage.StoredFileRepository storedFiles;
    @Autowired private com.algolearner.platform.assignment.AssignmentService assignmentService;
    @Autowired private com.algolearner.platform.quiz.QuizService quizService;
    @Autowired private com.algolearner.platform.practice.ClassPracticeService practiceService;
    @Autowired private com.algolearner.platform.problem.ProblemService problemService;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private AccountEntity teacher;
    private AccountEntity otherTeacher;
    private AccountEntity student;

    @BeforeEach
    void setup() {
        teacher = account("prof", AccountRole.TEACHER);
        otherTeacher = account("other", AccountRole.TEACHER);
        student = account("learner", AccountRole.STUDENT);
    }

    @Test
    void lessonLifecycleAndProtectionFlow() throws Exception {
        // Teacher creates classroom
        String classRes = mvc.perform(post("/api/v1/classrooms")
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Data Structures\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String classId = objectMapper.readTree(classRes).path("data").path("id").asText();
        String code = objectMapper.readTree(classRes).path("data").path("classCode").asText();

        // Student joins
        mvc.perform(post("/api/v1/classrooms/join")
                        .with(actor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        // Teacher creates lesson (Draft)
        String lessonRes = mvc.perform(post("/api/v1/classrooms/{classId}/lessons", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Binary Trees\",\"content\":\"Tree traversal methods\",\"completionMode\":\"MANUAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String lessonId = objectMapper.readTree(lessonRes).path("data").path("id").asText();

        // Student cannot view Draft lesson -> 403
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lessonId)
                        .with(actor(student)))
                .andExpect(status().isForbidden());

        // Teacher adds material
        mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/materials", classId, lessonId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"trees.pdf\",\"fileUrl\":\"https://storage.example.test/trees.pdf\",\"fileType\":\"PDF\",\"fileSize\":1048576}"))
                .andExpect(status().isBadRequest());

        // Teacher publishes lesson
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/publish", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // Student can now view published lesson
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lessonId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Binary Trees"));

        // Student marks lesson as completed
        mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/complete", classId, lessonId)
                        .with(actor(student)))
                .andExpect(status().isNoContent());

        // Teacher tries hard delete -> BLOCKED because student progress exists
        mvc.perform(delete("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_HARD_DELETE_LESSON"));

        // Teacher archives lesson -> succeeds
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/archive", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        // Student can no longer access archived lesson
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lessonId)
                        .with(actor(student)))
                .andExpect(status().isForbidden());

        // Teacher restores lesson
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/restore", classId, lessonId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void capabilitiesActivitiesMaterialRemovalAndEnrichedDetailsFlow() throws Exception {
        String classRes = mvc.perform(post("/api/v1/classrooms")
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algorithms\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID classId = UUID.fromString(objectMapper.readTree(classRes).path("data").path("id").asText());
        String code = objectMapper.readTree(classRes).path("data").path("classCode").asText();

        mvc.perform(post("/api/v1/classrooms/join")
                        .with(actor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        // Capabilities: teacher allowed, student forbidden
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/capabilities", classId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityLinkingAvailable").value(true))
                .andExpect(jsonPath("$.data.maxFileSizeBytes").value(20971520))
                .andExpect(jsonPath("$.data.allowedExtensions[0]").value("pdf"));
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/capabilities", classId)
                        .with(actor(student)))
                .andExpect(status().isForbidden());

        // Create two assignments (one draft, one published)
        var draftAsg = assignmentService.create(teacher.getId(), classId, new com.algolearner.platform.assignment.AssignmentDtos.CreateAssignmentRequest(
                "Draft Lab", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        var pubAsg = assignmentService.create(teacher.getId(), classId, new com.algolearner.platform.assignment.AssignmentDtos.CreateAssignmentRequest(
                "Published Lab", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        assignmentService.publish(teacher.getId(), classId, pubAsg.id());
        entityManager.flush();

        var quiz = quizService.create(teacher.getId(), classId, new com.algolearner.platform.quiz.QuizDtos.Configure(
                "Pop Quiz", true, null, null, 600, 3, false, false, false,
                com.algolearner.platform.quiz.QuizDtos.ReleasePolicy.IMMEDIATE,
                com.algolearner.platform.quiz.QuizDtos.TimeoutMode.AUTO_SUBMIT,
                List.of(new com.algolearner.platform.quiz.QuizDtos.Question(
                        UUID.randomUUID(), "Q1",
                        com.algolearner.platform.quiz.QuizDtos.QuestionType.SINGLE_CHOICE,
                        com.algolearner.platform.quiz.QuizDtos.Scoring.ALL_OR_NOTHING,
                        java.math.BigDecimal.ONE,
                        List.of(
                                new com.algolearner.platform.quiz.QuizDtos.Option(UUID.randomUUID(), "Yes", true),
                                new com.algolearner.platform.quiz.QuizDtos.Option(UUID.randomUUID(), "No", false))))));
        quizService.publish(teacher.getId(), classId, quiz.id());

        var admin = account("admin", AccountRole.ADMIN);
        var problemId = problemService.create(teacher.getId(), new com.algolearner.platform.problem.ProblemService.Content(
                "Two Sum", "Find indices",
                java.util.Set.of(com.algolearner.platform.judge.JudgeGateway.Language.PYTHON),
                List.of(new com.algolearner.platform.judge.JudgeGateway.TestCaseSnapshot("1", "1", true, false)),
                new com.algolearner.platform.judge.JudgeGateway.Limits(10, 2000, 65536),
                com.algolearner.platform.judge.JudgeGateway.Feedback.PASS_FAIL));
        problemService.submitReview(teacher.getId(), problemId);
        problemService.review(admin.getId(), problemId, true, null);
        UUID practiceId = practiceService.copy(teacher.getId(), classId, problemId);
        practiceService.publish(teacher.getId(), classId, practiceId);

        // Activities endpoint lists Assignment, Quiz, and Practice with linkedLessonId = null; student forbidden
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/activities", classId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[?(@.id=='" + pubAsg.id() + "')].title").value(org.hamcrest.Matchers.contains("Published Lab")))
                .andExpect(jsonPath("$.data[?(@.id=='" + pubAsg.id() + "')].available").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.data[?(@.id=='" + draftAsg.id() + "')].available").value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.data[?(@.id=='" + quiz.id() + "')].type").value(org.hamcrest.Matchers.contains("QUIZ")))
                .andExpect(jsonPath("$.data[?(@.id=='" + quiz.id() + "')].available").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.data[?(@.id=='" + practiceId + "')].type").value(org.hamcrest.Matchers.contains("PRACTICE")))
                .andExpect(jsonPath("$.data[?(@.id=='" + practiceId + "')].available").value(org.hamcrest.Matchers.contains(true)));
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/activities", classId)
                        .with(actor(student)))
                .andExpect(status().isForbidden());

        // Create lesson 1 (manual completion): initially canDelete = true
        String lesson1Res = mvc.perform(post("/api/v1/classrooms/{classId}/lessons", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lesson 1\",\"content\":\"Intro\",\"completionMode\":\"MANUAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.canDelete").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID lesson1Id = UUID.fromString(objectMapper.readTree(lesson1Res).path("data").path("id").asText());

        // Attach and remove material
        var file = new com.algolearner.platform.storage.StoredFileEntity();
        file.setId(UUID.randomUUID());
        file.setUploaderId(teacher.getId());
        file.setClassroomId(classId);
        file.setObjectKey(teacher.getId() + "/" + file.getId());
        file.setName("slides.pdf");
        file.setMediaType("application/pdf");
        file.setKind(com.algolearner.platform.storage.FileKind.PDF);
        file.setSize(256L);
        file.setSha256("a".repeat(64));
        file.setPurpose(com.algolearner.platform.storage.FilePurpose.LESSON_MATERIAL);
        file.setState(com.algolearner.platform.storage.FileState.READY);
        file.setObjectUploaded(true);
        storedFiles.saveAndFlush(file);

        String matRes = mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/materials", classId, lesson1Id)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + file.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String materialId = objectMapper.readTree(matRes).path("data").path("id").asText();

        mvc.perform(delete("/api/v1/classrooms/{classId}/lessons/{lessonId}/materials/{materialId}", classId, lesson1Id, materialId)
                        .with(actor(student)))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/classrooms/{classId}/lessons/{lessonId}/materials/{materialId}", classId, lesson1Id, materialId)
                        .with(actor(teacher)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lesson1Id)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials").isEmpty());

        // Publish lesson 1, student completes it -> teacher detail now has canDelete == false
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/publish", classId, lesson1Id)
                        .with(actor(teacher)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/complete", classId, lesson1Id)
                        .with(actor(student)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lesson1Id)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(false));

        // Archive lesson 1 -> previousStatus == PUBLISHED
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/archive", classId, lesson1Id)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.previousStatus").value("PUBLISHED"));

        // Create lesson 2, link Published Lab & configure completion rule -> detail includes linked item metadata & requiredItemIds
        String lesson2Res = mvc.perform(post("/api/v1/classrooms/{classId}/lessons", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lesson 2\",\"content\":\"Practice\",\"completionMode\":\"MANUAL\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID lesson2Id = UUID.fromString(objectMapper.readTree(lesson2Res).path("data").path("id").asText());

        mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/items", classId, lesson2Id)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"ASSIGNMENT\",\"itemId\":\"" + pubAsg.id() + "\"}"))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/v1/classrooms/{classId}/lessons/{lessonId}/completion-rule", classId, lesson2Id)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"ALL\",\"conditions\":[{\"itemType\":\"ASSIGNMENT\",\"itemId\":\"" + pubAsg.id() + "\",\"requirement\":\"SUBMITTED\"}]}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/activities", classId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + pubAsg.id() + "')].linkedLessonId").value(org.hamcrest.Matchers.contains(lesson2Id.toString())));

        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, lesson2Id)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(false))
                .andExpect(jsonPath("$.data.requiredItemIds[0]").value(pubAsg.id().toString()))
                .andExpect(jsonPath("$.data.linkedItems[0].title").value("Published Lab"))
                .andExpect(jsonPath("$.data.linkedItems[0].available").value(true));
    }

    @Test
    void studentDetailHidesDraftRequiredItemIdsAndOwnerMetadataAndOtherTeacherIsForbidden() throws Exception {
        String classRes = mvc.perform(post("/api/v1/classrooms")
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Privacy Class\",\"joinMode\":\"AUTO_JOIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID classId = UUID.fromString(objectMapper.readTree(classRes).path("data").path("id").asText());
        String code = objectMapper.readTree(classRes).path("data").path("classCode").asText();

        mvc.perform(post("/api/v1/classrooms/join")
                        .with(actor(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classCode\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        // Fresh published manual lesson: teacher sees canDelete=true, student sees canDelete=false
        String manualLessonRes = mvc.perform(post("/api/v1/classrooms/{classId}/lessons", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Manual Lesson\",\"content\":\"Body\",\"completionMode\":\"MANUAL\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID manualLessonId = UUID.fromString(objectMapper.readTree(manualLessonRes).path("data").path("id").asText());

        var file = new com.algolearner.platform.storage.StoredFileEntity();
        file.setId(UUID.randomUUID());
        file.setUploaderId(teacher.getId());
        file.setClassroomId(classId);
        file.setObjectKey(teacher.getId() + "/" + file.getId());
        file.setName("handout.pdf");
        file.setMediaType("application/pdf");
        file.setKind(com.algolearner.platform.storage.FileKind.PDF);
        file.setSize(128L);
        file.setSha256("b".repeat(64));
        file.setPurpose(com.algolearner.platform.storage.FilePurpose.LESSON_MATERIAL);
        file.setState(com.algolearner.platform.storage.FileState.READY);
        file.setObjectUploaded(true);
        storedFiles.saveAndFlush(file);

        String matRes = mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/materials", classId, manualLessonId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + file.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String materialId = objectMapper.readTree(matRes).path("data").path("id").asText();

        // Non-owner teacher gets 403 FORBIDDEN_OPERATION on capabilities, activities, and removeMaterial
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/capabilities", classId)
                        .with(actor(otherTeacher)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/activities", classId)
                        .with(actor(otherTeacher)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
        mvc.perform(delete("/api/v1/classrooms/{classId}/lessons/{lessonId}/materials/{materialId}", classId, manualLessonId, materialId)
                        .with(actor(otherTeacher)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));

        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/publish", classId, manualLessonId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(true));
        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, manualLessonId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canDelete").value(false))
                .andExpect(jsonPath("$.data.previousStatus").value(org.hamcrest.Matchers.nullValue()));

        // Published AUTO_ITEMS lesson linked to a DRAFT assignment: student receives empty linkedItems and empty requiredItemIds
        var draftAsg = assignmentService.create(teacher.getId(), classId, new com.algolearner.platform.assignment.AssignmentDtos.CreateAssignmentRequest(
                "Secret Draft Lab", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        entityManager.flush();

        String autoLessonRes = mvc.perform(post("/api/v1/classrooms/{classId}/lessons", classId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Auto Lesson\",\"content\":\"Body\",\"completionMode\":\"MANUAL\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID autoLessonId = UUID.fromString(objectMapper.readTree(autoLessonRes).path("data").path("id").asText());

        mvc.perform(post("/api/v1/classrooms/{classId}/lessons/{lessonId}/items", classId, autoLessonId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"ASSIGNMENT\",\"itemId\":\"" + draftAsg.id() + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/classrooms/{classId}/lessons/{lessonId}/completion-rule", classId, autoLessonId)
                        .with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"ALL\",\"conditions\":[{\"itemType\":\"ASSIGNMENT\",\"itemId\":\"" + draftAsg.id() + "\",\"requirement\":\"SUBMITTED\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/classrooms/{classId}/lessons/{lessonId}/publish", classId, autoLessonId)
                        .with(actor(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiredItemIds[0]").value(draftAsg.id().toString()));

        mvc.perform(get("/api/v1/classrooms/{classId}/lessons/{lessonId}", classId, autoLessonId)
                        .with(actor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedItems").isEmpty())
                .andExpect(jsonPath("$.data.requiredItemIds").isEmpty())
                .andExpect(jsonPath("$.data.canDelete").value(false))
                .andExpect(jsonPath("$.data.previousStatus").value(org.hamcrest.Matchers.nullValue()));
    }

    private AccountEntity account(String username, AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername(username + UUID.randomUUID());
        account.setFullName("User " + username);
        account.setEmail(username + UUID.randomUUID() + "@example.test");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash("unused-test-fixture-hash");
        AccountEntity saved = accounts.saveAndFlush(account);
        testAccounts.add(saved);
        return saved;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor actor(AccountEntity account) {
        return authentication(new UsernamePasswordAuthenticationToken(
                account.getId().toString(), "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))
        ));
    }
}
