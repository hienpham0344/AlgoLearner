package com.algolearner.platform.grading;

import static org.assertj.core.api.Assertions.assertThat;

import com.algolearner.platform.AlgoLearnerApplication;
import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.auth.AuthSessionService;
import com.algolearner.platform.classroom.ClassroomDtos;
import com.algolearner.platform.classroom.ClassroomService;
import com.algolearner.platform.classroom.JoinMode;
import com.algolearner.platform.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=gradebook-restart-test-secret-with-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class GradebookRestartIntegrationTest extends PostgresIntegrationTest {
    private static final String JWT_SECRET = "gradebook-restart-test-secret-with-32-bytes";
    private static final String PUBLICATION = "{\"scope\":\"CLASS\",\"expectedVersion\":1}";
    @Autowired JdbcTemplate testDatabase;
    @Autowired ObjectMapper json;

    @Test
    void readsSchemePublicationHistoryAndNotificationOverHttpAfterRestart() throws Exception {
        var schema = testDatabase.queryForObject("SELECT current_schema()", String.class);
        String gradebook;
        String teacherToken;
        String studentToken;
        JsonNode receipt;
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            try (var first = start(schema, false)) {
                var accounts = first.getBean(AccountRepository.class);
                var teacher = account(accounts, AccountRole.TEACHER);
                var student = account(accounts, AccountRole.STUDENT);
                var classrooms = first.getBean(ClassroomService.class);
                var classroom = classrooms.create(teacher.getId(), new ClassroomDtos.Create(
                        "Restart gradebook", null, JoinMode.AUTO_JOIN, null, null)).id();
                classrooms.directAdd(teacher.getId(), classroom, new ClassroomDtos.DirectAdd(student.getUsername()));
                var source = first.getBean(JdbcTemplate.class);
                var full = quiz(source, classroom, student.getId(), 100);
                var half = quiz(source, classroom, student.getId(), 50);
                var sessions = first.getBean(AuthSessionService.class);
                teacherToken = sessions.issue(teacher, false).token();
                studentToken = sessions.issue(student, false).token();
                gradebook = "/api/v1/classrooms/" + classroom + "/gradebook";
                send(client, first, teacherToken, "PUT", gradebook + "/scheme", weights(full, half, 0, 20, 80), null, 200);
                receipt = send(client, first, teacherToken, "POST", gradebook + "/publications", PUBLICATION, "restart-receipt", 201);
                send(client, first, teacherToken, "PUT", gradebook + "/visibility",
                        "{\"showOverall\":true,\"expectedVersion\":2}", null, 200);
                send(client, first, teacherToken, "PUT", gradebook + "/scheme", weights(full, half, 3, 80, 20), null, 200);
                source.update("UPDATE tbl_quiz_attempt SET score=90 WHERE quiz_id=?", full);
                first.getBean(GradebookRefreshRepository.class).enqueue(new GradeSourceChanged(classroom, student.getId()));
                assertThat(source.queryForObject("SELECT count(*) FROM tbl_gradebook_refresh WHERE student_id=?",
                        Long.class, student.getId())).isEqualTo(1L);
            }
            try (var restarted = start(schema, true)) {
                var scheme = send(client, restarted, teacherToken, "GET", gradebook + "/scheme", null, null, 200);
                assertThat(scheme.path("revisionNumber").asInt()).isEqualTo(2);
                var database = restarted.getBean(JdbcTemplate.class);
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                        assertThat(database.queryForObject("SELECT count(*) FROM tbl_gradebook_refresh", Long.class)).isZero());
                var me = send(client, restarted, studentToken, "GET", gradebook + "/me", null, null, 200);
                assertThat(me.at("/overall/finalGrade/percentage").decimalValue()).isEqualByComparingTo("82");
                var replay = send(client, restarted, teacherToken, "POST", gradebook + "/publications", PUBLICATION, "restart-receipt", 200);
                assertThat(replay).isEqualTo(receipt);
                var history = send(client, restarted, teacherToken, "GET", gradebook + "/history", null, null, 200);
                assertThat(history.at("/items/0/action").asText()).isEqualTo("SOURCE_RECALCULATED");
                var notifications = send(client, restarted, studentToken, "GET", "/api/v1/notifications", null, null, 200);
                assertThat(notifications.at("/items/0/type").asText()).isEqualTo("GRADEBOOK_FINAL_CHANGED");
            }
        }
    }

    private ServletWebServerApplicationContext start(String schema, boolean enableWorker) {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(AlgoLearnerApplication.class)
                .profiles("internal-auth", "test", enableWorker ? "gradebook-worker-test" : "gradebook-restart-setup").run(
                        "--server.port=0", "--server.address=127.0.0.1", "--logging.level.root=WARN",
                        "--spring.main.banner-mode=off", "--app.security.jwt-secret=" + JWT_SECRET,
                        "--spring.datasource.url=" + System.getenv("TEST_DB_URL"),
                        "--spring.datasource.username=" + System.getenv("TEST_DB_USERNAME"),
                        "--spring.datasource.password=" + System.getenv("TEST_DB_PASSWORD"),
                        "--spring.datasource.hikari.schema=" + schema, "--spring.datasource.hikari.maximum-pool-size=4",
                        "--spring.datasource.hikari.minimum-idle=1", "--app.gradebook.refresh.delay-ms=100",
                        "--spring.flyway.default-schema=" + schema, "--spring.flyway.schemas=" + schema,
                        "--spring.jpa.properties.hibernate.default_schema=" + schema);
    }

    private JsonNode send(HttpClient client, ServletWebServerApplicationContext context, String token,
                          String method, String path, String payload, String key, int expectedStatus) throws Exception {
        var address = URI.create("http://127.0.0.1:" + context.getWebServer().getPort() + path);
        var request = HttpRequest.newBuilder(address).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        if (payload != null) request.header("Content-Type", "application/json");
        request.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(expectedStatus);
        return json.readTree(response.body()).path("data");
    }

    private static String weights(UUID full, UUID half, int version, int first, int second) {
        return """
                {"mode":"WEIGHTED","expectedVersion":%d,"reason":"Restart verification","items":[
                 {"sourceType":"QUIZ","sourceId":"%s","weight":%d,"required":true},
                 {"sourceType":"QUIZ","sourceId":"%s","weight":%d,"required":true}]}
                """.formatted(version, full, first, half, second);
    }

    private static UUID quiz(JdbcTemplate jdbc, UUID classroom, UUID student, int score) {
        var item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_quiz(id,classroom_id,title,status,graded,max_attempts,questions_json)
                VALUES (?,?,'Quiz','PUBLISHED',true,1,'[]'::jsonb)
                """, item, classroom);
        jdbc.update("""
                INSERT INTO tbl_quiz_attempt(id,quiz_id,student_id,status,started_at,submitted_at,score,grade_published,snapshot_json)
                VALUES (?,?,?,'SUBMITTED',now(),now(),?,true,'{}'::jsonb)
                """, UUID.randomUUID(), item, student, score);
        return item;
    }

    private static AccountEntity account(AccountRepository accounts, AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("restart_" + account.getId().toString().replace("-", ""));
        account.setEmail(account.getId() + "@example.test");
        account.setPasswordHash("unused");
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }
}
