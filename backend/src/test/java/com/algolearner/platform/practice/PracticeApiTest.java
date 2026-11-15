package com.algolearner.platform.practice;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.classroom.*;
import com.algolearner.platform.common.security.AccountRepository;
import com.algolearner.platform.profile.PracticeRepository;
import com.algolearner.platform.profile.ProfileRepository;
import com.algolearner.platform.profile.dashboard.DashboardRepository;
import com.algolearner.platform.profile.dashboard.DashboardResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:practice-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false",
        "app.security.jwt.issuer=https://issuer.example.test", "app.security.jwt.audience=practice-tests",
        "app.security.jwt.jwk-set-uri=https://issuer.example.test/.well-known/jwks.json"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PracticeApiTest {
    private static final String BASE = "/api/v1/classrooms/{c}/practices";
    private static final String HIDDEN_OUT = "HIDDEN-EXPECTED-42";
    private static volatile PracticeVerdict nextVerdict = PracticeVerdict.ACCEPTED;
    private static final AtomicBoolean judgeDown = new AtomicBoolean(false);
    private static final Set<UUID> archivedLessons = ConcurrentHashMap.newKeySet();

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2026-11-15T03:30:00Z"), ZoneOffset.UTC); }
        @Bean @Primary TestAccounts testAccounts() { return new TestAccounts(); }
        @Bean @Primary LessonStatusPort fakeLessons() { return archivedLessons::contains; }
        @Bean @Primary PracticeJudgePort fakeJudge() {
            return new PracticeJudgePort() {
                @Override public RunResult run(RunRequest r) {
                    if (judgeDown.get()) throw new IllegalStateException("judge timed out");
                    return new RunResult(r.testcases().stream().map(c -> new CaseResult(c.index(), nextVerdict, "out", "", 3)).toList());
                }
                @Override public SubmitResult submit(SubmitRequest r) {
                    if (judgeDown.get()) throw new IllegalStateException("judge crashed");
                    return new SubmitResult(nextVerdict, 7, 1024, "{\"internal\":\"" + HIDDEN_OUT + "\"}");
                }
            };
        }
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
        void add(AccountEntity a) {
            records.add(new Account(a.getId(), a.getUsername(), a.getRole().name(), a.getStatus().name(), a.getCreatedAt()));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.algolearner.platform.auth.AccountRepository accounts;
    @Autowired TestAccounts testAccounts;
    @Autowired ClassroomRepository classrooms;
    @Autowired ClassroomMembershipRepository memberships;
    @Autowired PracticeSubmissionRepository submissions;
    @Autowired PracticeCompletionRepository completions;
    @Autowired ClassroomPracticeRepository practices;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    private AccountEntity teacher;
    private AccountEntity otherTeacher;
    private AccountEntity student;
    private AccountEntity otherStudent;
    private AccountEntity outsider;
    private ClassroomEntity classroom;

    @BeforeEach
    void setUp() {
        nextVerdict = PracticeVerdict.ACCEPTED;
        judgeDown.set(false);
        archivedLessons.clear();
        teacher = account("teacher", AccountRole.TEACHER);
        otherTeacher = account("other", AccountRole.TEACHER);
        student = account("student", AccountRole.STUDENT);
        otherStudent = account("student2", AccountRole.STUDENT);
        outsider = account("outsider", AccountRole.STUDENT);
        classroom = new ClassroomEntity();
        classroom.setId(UUID.randomUUID());
        classroom.setOwner(teacher);
        classroom.setName("Algorithms");
        classroom.setStatus(ClassroomStatus.ACTIVE);
        classroom.setClassCode(UUID.randomUUID().toString().substring(0, 8));
        classroom.setJoiningOpen(true);
        classroom.setJoinMode(JoinMode.AUTO_JOIN);
        classrooms.save(classroom);
        join(student);
        join(otherStudent);
    }

    // ================================================================ PRC-01 (student)

    @Test @DisplayName("TC-PRC-01-01 RUN with sample and custom input returns feedback, persists nothing")
    void tcPrc0101_run() throws Exception {
        String id = publishedPractice(null);
        mvc.perform(post(BASE + "/{p}/run", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.cases.length()").value(1)).andExpect(jsonPath("$.data.cases[0].expectedOutput").value("3"));
        mvc.perform(post(BASE + "/{p}/run", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"JAVA\",\"sourceCode\":\"class Main {}\",\"customInput\":\"7 8\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cases[0].input").value("7 8"))
                .andExpect(jsonPath("$.data.cases[0].expectedOutput").isEmpty());
        assertThat(submissions.count()).isZero();
        assertThat(completions.count()).isZero();
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(student)))
                .andExpect(jsonPath("$.data.completionStatus").value("NOT_COMPLETED"));
    }

    @Test @DisplayName("TC-PRC-01-01 RUN is rate limited to 5 per minute and custom input is capped at 64 KB")
    void tcPrc0101_limits() throws Exception {
        String id = publishedPractice(null);
        String tooBig = "x".repeat(70_000);
        mvc.perform(post(BASE + "/{p}/run", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"JAVA\",\"sourceCode\":\"c\",\"customInput\":\"" + tooBig + "\"}"))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post(BASE + "/{p}/run", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(BASE + "/{p}/run", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
        mvc.perform(post(BASE + "/{p}/run", classroom.getId(), id).with(actor(otherStudent)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("TC-PRC-01-02 SUBMIT pins the testcase version, stores the verdict and completes on ACCEPTED")
    void tcPrc0102_submit() throws Exception {
        String id = publishedPractice(null);
        String body = mvc.perform(post(BASE + "/{p}/submit", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.testcaseVersion").value(1)).andExpect(jsonPath("$.data.runtimeMs").value(7))
                .andExpect(jsonPath("$.data.memoryKb").value(1024))
                .andExpect(jsonPath("$.data.submittedAt").value("2026-11-15T03:30:00Z")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(HIDDEN_OUT).doesNotContain("judgeDetails");
        assertThat(submissions.count()).isEqualTo(1);
        var completion = completions.findAll().get(0);
        assertThat(completion.getId().getStudentId()).isEqualTo(student.getId());
        assertThat(completion.getId().getPracticeId()).isEqualTo(UUID.fromString(id));
        assertThat(completion.getFirstAcceptedSubmission().getId()).isEqualTo(submissions.findAll().get(0).getId());
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(student)))
                .andExpect(jsonPath("$.data.completionStatus").value("COMPLETED"));
    }

    @Test @DisplayName("TC-PRC-01-03 unlimited attempts, and nothing carries a deadline or a score")
    void tcPrc0103_unlimitedAttempts() throws Exception {
        String id = publishedPractice(null);
        nextVerdict = PracticeVerdict.WRONG_ANSWER;
        String last = "";
        for (int i = 0; i < 8; i++) {
            last = mvc.perform(post(BASE + "/{p}/submit", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        }
        assertThat(submissions.count()).isEqualTo(8);
        assertThat(last).doesNotContainIgnoringCase("deadline").doesNotContainIgnoringCase("score");
        String detail = mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(student))).andReturn().getResponse().getContentAsString();
        String list = mvc.perform(get(BASE, classroom.getId()).with(actor(student))).andReturn().getResponse().getContentAsString();
        assertThat(detail + list).doesNotContainIgnoringCase("deadline").doesNotContainIgnoringCase("score");
        mvc.perform(get(BASE + "/{p}/submissions/me", classroom.getId(), id).with(actor(student)))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(8));
    }

    @Test @DisplayName("TC-PRC-01-04 only ACCEPTED completes, first acceptance sticks, students are independent")
    void tcPrc0104_completion() throws Exception {
        String id = publishedPractice(null);
        nextVerdict = PracticeVerdict.WRONG_ANSWER;
        submit(student, id);
        nextVerdict = PracticeVerdict.TIME_LIMIT_EXCEEDED;
        submit(student, id);
        assertThat(completions.count()).isZero();
        nextVerdict = PracticeVerdict.ACCEPTED;
        submit(student, id);
        assertThat(completions.count()).isEqualTo(1);
        var firstAccepted = completions.findAll().get(0).getFirstAcceptedSubmission().getId();
        submit(student, id);
        assertThat(completions.count()).isEqualTo(1);
        assertThat(completions.findAll().get(0).getFirstAcceptedSubmission().getId()).isEqualTo(firstAccepted);

        JsonNode rows = json.readTree(mvc.perform(get(BASE + "/{p}/progress", classroom.getId(), id).with(actor(teacher)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("items");
        assertThat(rows).hasSize(2);
        for (JsonNode row : rows) {
            boolean mine = row.path("studentId").asText().equals(student.getId().toString());
            assertThat(row.path("status").asText()).isEqualTo(mine ? "COMPLETED" : "NOT_COMPLETED");
        }
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(otherStudent)))
                .andExpect(jsonPath("$.data.completionStatus").value("NOT_COMPLETED"));
    }

    @Test @DisplayName("TC-PRC-01-05 DRAFT practice or archived lesson rejects RUN/SUBMIT; old history stays")
    void tcPrc0105_unavailable() throws Exception {
        String draft = createPractice(createJson("", true));
        for (String path : List.of("/run", "/submit")) {
            mvc.perform(post(BASE + "/{p}" + path, classroom.getId(), draft).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_AVAILABLE"));
        }
        UUID lesson = UUID.randomUUID();
        String linked = publishedPractice(lesson);
        String submitted = submit(student, linked);
        archivedLessons.add(lesson);
        for (String path : List.of("/run", "/submit")) {
            mvc.perform(post(BASE + "/{p}" + path, classroom.getId(), linked).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_AVAILABLE"));
        }
        assertThat(submissions.count()).isEqualTo(1);
        mvc.perform(get(BASE + "/{p}/submissions/{s}", classroom.getId(), linked, submitted).with(actor(student))).andExpect(status().isOk());
        mvc.perform(get(BASE + "/{p}/submissions/me", classroom.getId(), linked).with(actor(student)))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test @DisplayName("TC-PRC-01-06 suspended student or read-only class: reading works, RUN/SUBMIT are 403, data kept")
    void tcPrc0106_readOnly() throws Exception {
        String id = publishedPractice(null);
        submit(student, id);
        long before = submissions.count();

        student.setStatus(AccountStatus.SUSPENDED);
        accounts.saveAndFlush(student);
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(student))).andExpect(status().isOk());
        mvc.perform(post(BASE + "/{p}/submit", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/{p}/run", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isForbidden());

        student.setStatus(AccountStatus.ACTIVE);
        accounts.saveAndFlush(student);
        teacher.setStatus(AccountStatus.SUSPENDED);
        accounts.saveAndFlush(teacher);
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(student))).andExpect(status().isOk());
        mvc.perform(post(BASE + "/{p}/submit", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("CLASSROOM_READ_ONLY"));
        assertThat(submissions.count()).isEqualTo(before);
        assertThat(completions.count()).isEqualTo(1);
    }

    @Test @DisplayName("TC-PRC-01-07 a student outside the class gets 403")
    void tcPrc0107_nonMember() throws Exception {
        String id = publishedPractice(null);
        mvc.perform(get(BASE, classroom.getId()).with(actor(outsider))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(outsider))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/{p}/run", classroom.getId(), id).with(actor(outsider)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/{p}/submit", classroom.getId(), id).with(actor(outsider)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE, classroom.getId())).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("TC-PRC-01-08 judge outage: 503, no fake verdict, nothing stored or completed")
    void tcPrc0108_judgeDown() throws Exception {
        String id = publishedPractice(null);
        judgeDown.set(true);
        mvc.perform(post(BASE + "/{p}/submit", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("JUDGE_SERVICE_UNAVAILABLE"));
        mvc.perform(post(BASE + "/{p}/run", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isServiceUnavailable());
        assertThat(submissions.count()).isZero();
        assertThat(completions.count()).isZero();
        judgeDown.set(false);
        submit(student, id);
        assertThat(submissions.count()).isEqualTo(1);
    }

    @Test
    void studentNeverSeesHiddenTestcasesAndDraftsAreInvisible() throws Exception {
        String id = createPractice(createJson("", true));
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(student))).andExpect(status().isNotFound());
        mvc.perform(get(BASE, classroom.getId()).with(actor(student))).andExpect(jsonPath("$.data.items.length()").value(0));
        publish(id);

        String detail = mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sampleTestcases.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain(HIDDEN_OUT).doesNotContain("HIDDEN-INPUT");
        mvc.perform(get(BASE, classroom.getId()).with(actor(student))).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get(BASE + "/{p}/testcases", classroom.getId(), id).with(actor(student))).andExpect(status().isForbidden());
    }

    @Test
    void submissionsAreIsolatedBetweenStudents() throws Exception {
        String id = publishedPractice(null);
        String mine = submit(student, id);
        mvc.perform(get(BASE + "/{p}/submissions/{s}", classroom.getId(), id, mine).with(actor(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("class Main {}"));
        mvc.perform(get(BASE + "/{p}/submissions/{s}", classroom.getId(), id, mine).with(actor(otherStudent))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/{p}/submissions/me", classroom.getId(), id).with(actor(otherStudent)))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get(BASE + "/{p}/submissions", classroom.getId(), id).with(actor(otherStudent))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/{p}/submissions", classroom.getId(), id).with(actor(otherTeacher))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/{p}/submissions/{s}", classroom.getId(), id, mine).with(actor(teacher))).andExpect(status().isOk());
        mvc.perform(get(BASE + "/{p}/submissions", classroom.getId(), id).param("studentId", student.getId().toString()).with(actor(teacher)))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void disallowedLanguageIsBadRequest() throws Exception {
        String id = publishedPractice(null);
        mvc.perform(post(BASE + "/{p}/submit", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"PYTHON\",\"sourceCode\":\"print(1)\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("LANGUAGE_NOT_ALLOWED"));
    }

    // ================================================================ PRC-10 (teacher)

    @Test @DisplayName("TC-PRC-10-01 teacher creates a practice with limits, starter code and sample/hidden testcases")
    void tcPrc1001_create() throws Exception {
        String body = mvc.perform(post(BASE, classroom.getId()).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sum\",\"problemType\":\"FUNCTION_METHOD\",\"allowedLanguages\":[\"JAVA\",\"PYTHON\"],"
                                + "\"starterCode\":{\"JAVA\":\"class Solution {}\",\"PYTHON\":\"def solve(): pass\"},"
                                + "\"testcases\":[" + sampleCase() + "," + hiddenCase() + "]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.timeLimitMs").value(2000)).andExpect(jsonPath("$.data.memoryLimitMb").value(256))
                .andExpect(jsonPath("$.data.problemType").value("FUNCTION_METHOD"))
                .andExpect(jsonPath("$.data.starterCode.JAVA").value("class Solution {}"))
                .andExpect(jsonPath("$.data.testcaseVersion").value(1))
                .andExpect(jsonPath("$.data.sampleTestcases.length()").value(1))
                .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.endsWith("Z"))).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).path("data").path("id").asText();
        assertThat(body).doesNotContain(HIDDEN_OUT);
        mvc.perform(get(BASE + "/{p}/testcases", classroom.getId(), id).with(actor(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sample").value(true)).andExpect(jsonPath("$.data[1].sample").value(false));
        mvc.perform(post(BASE, classroom.getId()).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"allowedLanguages\":[],\"timeLimitMs\":5,\"memoryLimitMb\":9999}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(BASE + "/{p}", classroom.getId(), id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\",\"timeLimitMs\":1500}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Renamed")).andExpect(jsonPath("$.data.timeLimitMs").value(1500));
        mvc.perform(delete(BASE + "/{p}", classroom.getId(), id).with(actor(teacher))).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(teacher))).andExpect(status().isNotFound());
    }

    @Test @DisplayName("TC-PRC-10-01 a practice can be created PUBLISHED, but only with a sample and a hidden testcase")
    void tcPrc1001_initialPublished() throws Exception {
        mvc.perform(post(BASE, classroom.getId()).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"allowedLanguages\":[\"JAVA\"],\"status\":\"PUBLISHED\",\"testcases\":[" + sampleCase() + "]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("PRACTICE_NOT_READY"));
        mvc.perform(post(BASE, classroom.getId()).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"allowedLanguages\":[\"JAVA\"],\"status\":\"PUBLISHED\",\"testcases\":[" + sampleCase() + "," + hiddenCase() + "]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test @DisplayName("TC-PRC-10-02 lesson link: an archived lesson hides the practice")
    void tcPrc1002_lesson() throws Exception {
        UUID lesson = UUID.randomUUID();
        String id = publishedPractice(lesson);
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(teacher)))
                .andExpect(jsonPath("$.data.lessonId").value(lesson.toString())).andExpect(jsonPath("$.data.available").value(true));
        mvc.perform(get(BASE, classroom.getId()).with(actor(student))).andExpect(jsonPath("$.data.items.length()").value(1));

        archivedLessons.add(lesson);
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(teacher)))
                .andExpect(jsonPath("$.data.available").value(false)).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mvc.perform(get(BASE, classroom.getId()).with(actor(student))).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(student))).andExpect(status().isNotFound());

        String standalone = publishedPractice(null);
        mvc.perform(get(BASE + "/{p}", classroom.getId(), standalone).with(actor(teacher)))
                .andExpect(jsonPath("$.data.lessonId").isEmpty()).andExpect(jsonPath("$.data.available").value(true));
    }

    @Test @DisplayName("TC-PRC-10-03 editing testcases after submissions bumps the version; old submissions keep theirs")
    void tcPrc1003_versioning() throws Exception {
        String id = publishedPractice(null);
        String oldSubmission = submit(student, id);
        String hiddenId = hiddenTestcaseId(id);

        mvc.perform(put(BASE + "/{p}/testcases/{t}", classroom.getId(), id, hiddenId).param("rejudge", "true").with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"input\":\"NEW-IN\",\"expectedOutput\":\"NEW-OUT\",\"sample\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(teacher))).andExpect(jsonPath("$.data.testcaseVersion").value(2));
        mvc.perform(get(BASE + "/{p}/testcases", classroom.getId(), id).with(actor(teacher)))
                .andExpect(jsonPath("$.data.length()").value(2)).andExpect(jsonPath("$.data[1].input").value("NEW-IN"));
        // The bulk UPDATE bypasses the persistence context this test shares across requests; real requests each get their own.
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get(BASE + "/{p}/submissions/{s}", classroom.getId(), id, oldSubmission).with(actor(student)))
                .andExpect(jsonPath("$.data.testcaseVersion").value(1)).andExpect(jsonPath("$.data.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.rejudgeStatus").value("PENDING_REJUDGE"));
        assertThat(completions.count()).isEqualTo(1);
        assertThat(submissions.findAll().get(0).getTestcaseVersion()).isEqualTo(1);
        mvc.perform(post(BASE + "/{p}/submit", classroom.getId(), id).with(actor(otherStudent)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.testcaseVersion").value(2));
    }

    @Test @DisplayName("TC-PRC-10-03 without a rejudge request nothing is flagged; an unused version is edited in place")
    void tcPrc1003_noRejudge() throws Exception {
        String id = publishedPractice(null);
        String hiddenId = hiddenTestcaseId(id);
        mvc.perform(put(BASE + "/{p}/testcases/{t}", classroom.getId(), id, hiddenId).with(actor(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"input\":\"A\",\"expectedOutput\":\"B\",\"sample\":false}"))
                .andExpect(jsonPath("$.data.version").value(1));
        String oldSubmission = submit(student, id);
        mvc.perform(post(BASE + "/{p}/testcases", classroom.getId(), id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content(hiddenCase()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(get(BASE + "/{p}/submissions/{s}", classroom.getId(), id, oldSubmission).with(actor(student)))
                .andExpect(jsonPath("$.data.testcaseVersion").value(1)).andExpect(jsonPath("$.data.rejudgeStatus").isEmpty());
        mvc.perform(delete(BASE + "/{p}/testcases/{t}", classroom.getId(), id, hiddenTestcaseId(id)).with(actor(teacher))).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/{p}/testcases", classroom.getId(), id).with(actor(teacher))).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test @DisplayName("TC-PRC-10-04 a teacher who does not own the class cannot create, edit or delete (403)")
    void tcPrc1004_idor() throws Exception {
        mvc.perform(post(BASE, classroom.getId()).with(actor(otherTeacher)).contentType(MediaType.APPLICATION_JSON).content(createJson("", false)))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE, classroom.getId()).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(createJson("", false)))
                .andExpect(status().isForbidden());
        String id = publishedPractice(null);
        mvc.perform(patch(BASE + "/{p}", classroom.getId(), id).with(actor(otherTeacher)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hijack\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/{p}", classroom.getId(), id).with(actor(otherTeacher))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/{p}/status", classroom.getId(), id).with(actor(otherTeacher)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/{p}/testcases", classroom.getId(), id).with(actor(otherTeacher)).contentType(MediaType.APPLICATION_JSON).content(hiddenCase()))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/{p}/testcases", classroom.getId(), id).with(actor(otherTeacher))).andExpect(status().isForbidden());
        mvc.perform(get(BASE, classroom.getId()).with(actor(otherTeacher))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/{p}", classroom.getId(), id).with(actor(teacher))).andExpect(jsonPath("$.data.title").value("Sum of two"));
    }

    @Test @DisplayName("TC-PRC-10-05 deadline or maxScore > 0 is a 400; maxScore 0 is accepted")
    void tcPrc1005_ungradedAndNoDeadline() throws Exception {
        mvc.perform(post(BASE, classroom.getId()).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("\"deadline\":\"2026-12-01T00:00:00Z\",", false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("PRACTICE_SCOPE_VIOLATION"));
        mvc.perform(post(BASE, classroom.getId()).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("\"maxScore\":10,", false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("PRACTICE_SCOPE_VIOLATION"));
        assertThat(practices.count()).isZero();
        String id = createPractice(createJson("", false));
        mvc.perform(patch(BASE + "/{p}", classroom.getId(), id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"maxScore\":5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(BASE + "/{p}", classroom.getId(), id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"deadline\":\"2026-12-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE, classroom.getId()).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content(createJson("\"maxScore\":0,", false)))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("TC-PRC-10-06 archiving keeps every submission and completion")
    void tcPrc1006_archiveKeepsData() throws Exception {
        String id = publishedPractice(null);
        String mine = submit(student, id);
        submit(otherStudent, id);
        assertThat(submissions.count()).isEqualTo(2);
        assertThat(completions.count()).isEqualTo(2);

        mvc.perform(post(BASE + "/{p}/status", classroom.getId(), id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        assertThat(submissions.count()).isEqualTo(2);
        assertThat(completions.count()).isEqualTo(2);
        mvc.perform(get(BASE + "/{p}/submissions/{s}", classroom.getId(), id, mine).with(actor(student))).andExpect(status().isOk());
        mvc.perform(get(BASE + "/{p}/submissions", classroom.getId(), id).with(actor(teacher))).andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get(BASE, classroom.getId()).with(actor(student))).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(post(BASE + "/{p}/submit", classroom.getId(), id).with(actor(student)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/{p}", classroom.getId(), id).with(actor(teacher))).andExpect(status().isConflict());
        assertThat(submissions.count()).isEqualTo(2);
    }

    @Test
    void statusCanOnlyMoveForward() throws Exception {
        String id = createPractice(createJson("", false));
        mvc.perform(post(BASE + "/{p}/status", classroom.getId(), id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("PRACTICE_NOT_READY"));
        mvc.perform(post(BASE + "/{p}/status", classroom.getId(), id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("PRACTICE_INVALID_STATUS_TRANSITION"));
    }

    // ---- helpers
    private String publishedPractice(UUID lessonId) throws Exception {
        String lesson = lessonId == null ? "" : "\"lessonId\":\"" + lessonId + "\",";
        String id = createPractice(createJson(lesson, true));
        publish(id);
        return id;
    }

    private String createPractice(String body) throws Exception {
        String response = mvc.perform(post(BASE, classroom.getId()).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("id").asText();
    }

    private void publish(String id) throws Exception {
        mvc.perform(post(BASE + "/{p}/status", classroom.getId(), id).with(actor(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    private String submit(AccountEntity who, String practiceId) throws Exception {
        String body = mvc.perform(post(BASE + "/{p}/submit", classroom.getId(), practiceId).with(actor(who)).contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("id").asText();
    }

    private String hiddenTestcaseId(String practiceId) throws Exception {
        JsonNode all = json.readTree(mvc.perform(get(BASE + "/{p}/testcases", classroom.getId(), practiceId).with(actor(teacher)))
                .andReturn().getResponse().getContentAsString()).path("data");
        for (JsonNode t : all) if (!t.path("sample").asBoolean()) return t.path("id").asText();
        throw new AssertionError("no hidden testcase");
    }

    private static String sampleCase() { return "{\"input\":\"1 2\",\"expectedOutput\":\"3\",\"sample\":true}"; }
    private static String hiddenCase() { return "{\"input\":\"HIDDEN-INPUT\",\"expectedOutput\":\"" + HIDDEN_OUT + "\",\"sample\":false}"; }

    /** {@code extra} is spliced in front of the other fields and must end with a comma (or be empty). */
    private static String createJson(String extra, boolean withTestcases) {
        return "{" + extra + "\"title\":\"Sum of two\",\"description\":\"Add\",\"allowedLanguages\":[\"JAVA\"],"
                + "\"timeLimitMs\":2000,\"memoryLimitMb\":256"
                + (withTestcases ? ",\"testcases\":[" + sampleCase() + "," + hiddenCase() + "]" : "") + "}";
    }

    private static String codeBody() { return "{\"language\":\"JAVA\",\"sourceCode\":\"class Main {}\"}"; }

    private void join(AccountEntity who) {
        var m = new ClassroomMembershipEntity();
        m.setId(UUID.randomUUID());
        m.setClassroom(classroom);
        m.setStudent(who);
        m.setStatus(MembershipStatus.ACTIVE);
        m.setJoinedAt(Instant.parse("2026-11-01T00:00:00Z"));
        memberships.save(m);
    }

    private AccountEntity account(String username, AccountRole role) {
        var a = new AccountEntity();
        a.setId(UUID.randomUUID());
        a.setUsername(username + UUID.randomUUID());
        a.setEmail(username + UUID.randomUUID() + "@example.test");
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        AccountEntity saved = accounts.save(a);
        testAccounts.add(saved);
        return saved;
    }

    private static RequestPostProcessor actor(AccountEntity a) {
        return authentication(new UsernamePasswordAuthenticationToken(a.getId().toString(), "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + a.getRole().name()))));
    }
}
