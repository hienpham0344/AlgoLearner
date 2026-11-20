package com.algolearner.platform.classroom;

import com.algolearner.platform.announcement.AnnouncementService;
import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import com.algolearner.platform.lesson.LessonService;
import com.algolearner.platform.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ClassroomAuthorizationAcceptanceTest extends AuthorizationAcceptanceSupport {
    @Test
    void invalidClassCreateAndUpdateLeaveStoredClassUnchanged() throws Exception {
        var before = row("tbl_classroom", classroom.id());
        int owned = jdbc.queryForObject("SELECT count(*) FROM tbl_classroom WHERE owner_id=?", Integer.class, owner.getId());
        call(HttpMethod.POST, "/api/v1/classrooms", owner, Map.of("name", " ", "joinMode", "AUTO_JOIN"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, classPath(), owner, Map.of("name", " ", "description", "Unwanted change"))
                .andExpect(status().isBadRequest());
        assertThat(row("tbl_classroom", classroom.id())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_classroom WHERE owner_id=?", Integer.class, owner.getId())).isEqualTo(owned);
    }

    @Test
    void classHasOnlyActiveAndArchivedStatesAndDatabaseRejectsThirdState() throws Exception {
        assertThat(ClassroomStatus.values()).containsExactly(ClassroomStatus.ACTIVE, ClassroomStatus.ARCHIVED);
        call(HttpMethod.GET, classPath(), owner, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        call(HttpMethod.POST, classPath() + "/archive", owner, null).andExpect(status().isNoContent());
        call(HttpMethod.GET, classPath(), owner, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        var before = row("tbl_classroom", classroom.id());
        assertThatThrownBy(() -> jdbc.update("UPDATE tbl_classroom SET status='DRAFT' WHERE id=?", classroom.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(row("tbl_classroom", classroom.id())).isEqualTo(before);
    }

    @Test
    void ownerCanHardDeleteEmptyClass() throws Exception {
        assertThat(rows("tbl_classroom_membership")).isEmpty();
        assertThat(rows("tbl_classroom_join_request")).isEmpty();
        call(HttpMethod.DELETE, classPath(), owner, null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_classroom WHERE id=?", Integer.class, classroom.id())).isZero();
    }

    static Stream<Arguments> classMutations() {
        return Stream.of(Arguments.of("STUDENT", "archive"), Arguments.of("OTHER_TEACHER", "archive"),
                Arguments.of("STUDENT", "delete"), Arguments.of("OTHER_TEACHER", "delete"));
    }

    @ParameterizedTest @MethodSource("classMutations")
    void studentAndNonOwnerCannotArchiveOrDeleteClass(String actor, String action) throws Exception {
        addStudent(student);
        var before = row("tbl_classroom", classroom.id());
        var members = rows("tbl_classroom_membership");
        call(action.equals("archive") ? HttpMethod.POST : HttpMethod.DELETE,
                classPath() + (action.equals("archive") ? "/archive" : ""), actor(actor), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
        assertThat(row("tbl_classroom", classroom.id())).isEqualTo(before);
        assertThat(rows("tbl_classroom_membership")).isEqualTo(members);
    }

    @Test
    void ownerCanRoundTripFutureAndUnlimitedCodeExpiry() throws Exception {
        Instant expiry = Instant.parse("2100-01-01T00:00:00Z");
        call(HttpMethod.PATCH, classPath() + "/join-configuration", owner, codeConfig(expiry, true, JoinMode.AUTO_JOIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.codeExpiresAt").value(expiry.toString()));
        call(HttpMethod.GET, classPath(), owner, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.codeExpiresAt").value(expiry.toString()));
        call(HttpMethod.PATCH, classPath() + "/join-configuration", owner, codeConfig(null, true, JoinMode.AUTO_JOIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.codeExpiresAt").isEmpty());
        call(HttpMethod.GET, classPath(), owner, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.codeExpiresAt").isEmpty());
        assertThat(row("tbl_classroom", classroom.id()).get("code_expires_at")).isNull();
    }

    @Test
    void closingJoiningRetainsExistingMembershipAndReadAccess() throws Exception {
        addStudent(student);
        var before = rows("tbl_classroom_membership");
        call(HttpMethod.PATCH, classPath() + "/join-configuration", owner, codeConfig(null, false, JoinMode.AUTO_JOIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.joiningOpen").value(false));
        assertThat(rows("tbl_classroom_membership")).isEqualTo(before);
        call(HttpMethod.GET, classPath(), student, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(classroom.id().toString()));
    }

    @ParameterizedTest @ValueSource(strings = {"STUDENT", "OTHER_TEACHER"})
    void nonOwnerCannotApprovePendingRequestOrCreateMembership(String actor) throws Exception {
        classrooms.configureCode(owner.getId(), classroom.id(), new ClassroomDtos.CodeConfig(null, false, true, JoinMode.REQUIRE_APPROVAL));
        var pending = classrooms.join(student.getId(), new ClassroomDtos.JoinByCode(classroom.classCode()));
        var before = rows("tbl_classroom_join_request");
        call(HttpMethod.POST, classPath() + "/join-requests/" + pending.requestId() + "/approve", actor(actor), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
        assertThat(rows("tbl_classroom_join_request")).isEqualTo(before);
        assertThat(rows("tbl_classroom_membership")).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"UNREGISTERED", "NON_STUDENT"})
    void invitationsRejectUnregisteredEmailAndNonStudentWithoutCreatingNotification(String target) throws Exception {
        String email = target.equals("UNREGISTERED") ? UUID.randomUUID() + "@example.test" : otherTeacher.getEmail();
        var result = call(HttpMethod.POST, classPath() + "/invitations", owner,
                Map.of("email", email, "expiresAt", "2100-01-01T00:00:00Z"));
        result.andExpect(status().is(target.equals("UNREGISTERED") ? 404 : 400))
                .andExpect(jsonPath("$.errorCode").value(target.equals("UNREGISTERED") ? "STUDENT_NOT_FOUND" : "INVALID_STUDENT"));
        assertThat(rows("tbl_classroom_invitation")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_notification n JOIN tbl_classroom_invitation i ON i.id=n.classroom_invitation_id WHERE i.classroom_id=?", Integer.class, classroom.id())).isZero();
        assertThat(rows("tbl_classroom_membership")).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"STUDENT", "OTHER_TEACHER"})
    void studentCannotRemovePeerAndNonOwnerCannotRemoveMember(String actor) throws Exception {
        addStudent(student); addStudent(peer);
        var before = rows("tbl_classroom_membership");
        call(HttpMethod.DELETE, classPath() + "/members/" + peer.getId(), actor(actor), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN_OPERATION"));
        assertThat(rows("tbl_classroom_membership")).isEqualTo(before);
    }
}

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=authorization-acceptance-test-only-secret-at-least-32-bytes",
        "app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth", "test"})
@AutoConfigureMockMvc
abstract class AuthorizationAcceptanceSupport extends PostgresIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired ClassroomService classrooms;
    @Autowired AnnouncementService announcements;
    @Autowired LessonService lessons;
    @Autowired JdbcTemplate jdbc;
    AccountEntity owner, otherTeacher, student, peer;
    ClassroomDtos.View classroom;

    @BeforeEach
    void fixture() {
        owner = account(AccountRole.TEACHER); otherTeacher = account(AccountRole.TEACHER);
        student = account(AccountRole.STUDENT); peer = account(AccountRole.STUDENT);
        classroom = classrooms.create(owner.getId(), new ClassroomDtos.Create("Original Class", "Saved description", JoinMode.AUTO_JOIN, null, null));
    }

    String classPath() { return "/api/v1/classrooms/" + classroom.id(); }
    AccountEntity actor(String actor) { return actor.equals("STUDENT") ? student : otherTeacher; }
    void addStudent(AccountEntity target) { classrooms.directAdd(owner.getId(), classroom.id(), new ClassroomDtos.DirectAdd(target.getUsername())); }
    RequestPostProcessor identity(AccountEntity actor) {
        return authentication(new UsernamePasswordAuthenticationToken(actor.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + actor.getRole().name()))));
    }
    ResultActions call(HttpMethod method, String path, AccountEntity actor, Object body) throws Exception {
        var request = request(method, path).with(identity(actor));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return http.perform(request);
    }
    Map<String, Object> row(String table, UUID id) { return jdbc.queryForMap("SELECT * FROM " + table + " WHERE id=?", id); }
    List<Map<String, Object>> rows(String table) { return jdbc.queryForList("SELECT * FROM " + table + " WHERE classroom_id=? ORDER BY id", classroom.id()); }
    Map<String, Object> codeConfig(Instant expiry, boolean open, JoinMode mode) {
        var input = new LinkedHashMap<String, Object>();
        input.put("codeExpiresAt", expiry); input.put("codeLocked", false); input.put("joiningOpen", open); input.put("joinMode", mode);
        return input;
    }
    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity(); account.setId(UUID.randomUUID());
        account.setUsername("authorization_" + UUID.randomUUID().toString().replace("-", ""));
        account.setEmail(account.getUsername() + "@example.test"); account.setPasswordHash("unused");
        account.setRole(role); account.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(account);
    }
}
