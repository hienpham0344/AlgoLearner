package com.algolearner.platform.course;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=course-flow-test-only-secret-at-least-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class PublicCourseFlowTest extends PostgresIntegrationTest {
    @Autowired AccountRepository accounts;
    @Autowired PublicCourseService service;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired MockMvc http;
    @Autowired ObjectMapper mapper;

    @Test
    void httpCourseFlowKeepsAdminReviewAndOwnLearningInSeparateRoutes() throws Exception {
        var teacher = account(AccountRole.TEACHER);var admin = account(AccountRole.ADMIN);var student = account(AccountRole.STUDENT);
        var response = http.perform(post("/api/v1/courses").with(identity(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"HTTP course\",\"description\":\"Learning\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true)).andReturn();
        UUID course = UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).at("/data/id").asText());
        var lessonResponse = http.perform(post("/api/v1/courses/{id}/lessons",course).with(identity(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lesson\",\"content\":\"Real content\"}"))
                .andExpect(status().isCreated()).andReturn();
        String unit = mapper.readTree(lessonResponse.getResponse().getContentAsString()).at("/data/id").asText();
        http.perform(get("/api/v1/courses/{id}",course).with(identity(student))).andExpect(status().isNotFound());
        http.perform(post("/api/v1/courses/{id}/review-requests",course).with(identity(teacher))).andExpect(status().isOk());
        http.perform(get("/api/v1/courses/contributions").with(identity(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.data").doesNotExist());
        http.perform(get("/api/v1/admin/courses").with(identity(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.pagination").exists())
                .andExpect(jsonPath("$.data.data").doesNotExist());
        http.perform(post("/api/v1/admin/courses/{id}/reviews",course).with(identity(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true}")).andExpect(status().isForbidden());
        http.perform(post("/api/v1/admin/courses/{id}/reviews",course).with(identity(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true}")).andExpect(status().isOk());
        http.perform(get("/api/v1/courses").with(identity(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.pagination").exists())
                .andExpect(jsonPath("$.data.data").doesNotExist());
        http.perform(get("/api/v1/courses/{id}",course).with(identity(admin))).andExpect(status().isForbidden());
        http.perform(post("/api/v1/courses/{id}/enrollments",course).with(identity(student))).andExpect(status().isOk());
        http.perform(post("/api/v1/courses/{id}/lessons/{unit}/completion",course,unit).with(identity(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.progressPercent").value(100.0))
                .andExpect(jsonPath("$.data.accountId").value(student.getId().toString()));
    }

    @Test
    void reviewedCourseEnrollmentProducesRealIdempotentOwnProgress() {
        var teacher = account(AccountRole.TEACHER);var admin = account(AccountRole.ADMIN);
        var student = account(AccountRole.STUDENT);var other = account(AccountRole.STUDENT);
        var course = service.create(teacher.getId(),new CourseDtos.Create("Algorithms","Public course"));
        var first = service.addUnit(teacher.getId(),course.id(),new CourseDtos.UnitRequest("Arrays","Content"));
        service.addUnit(teacher.getId(),course.id(),new CourseDtos.UnitRequest("Lists","Content"));
        assertThatThrownBy(() -> service.enroll(student.getId(),course.id())).isInstanceOf(AppException.class);
        service.submitReview(teacher.getId(),course.id());
        assertThatThrownBy(() -> service.review(teacher.getId(),course.id(),new CourseDtos.Review(true,null))).isInstanceOf(AppException.class);
        service.review(admin.getId(),course.id(),new CourseDtos.Review(true,null));
        assertThat(service.enroll(student.getId(),course.id()).completedUnits()).isZero();
        service.enroll(student.getId(),course.id());
        service.complete(student.getId(),course.id(),first.id());
        service.complete(student.getId(),course.id(),first.id());
        assertThat(service.progress(student.getId(),course.id()).progressPercent()).isEqualTo(50.0);
        assertThatThrownBy(() -> service.progress(other.getId(),course.id())).isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_course_enrollment WHERE course_id=?",Integer.class,course.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_audit_log WHERE action='PUBLIC_COURSE_REVIEWED' AND target_id=?",Integer.class,course.id().toString())).isEqualTo(1);
    }

    @Test
    void studentsCannotAuthorAndUnitsCannotCrossCourses() {
        var teacher = account(AccountRole.TEACHER);var admin = account(AccountRole.ADMIN);var student = account(AccountRole.STUDENT);
        assertThatThrownBy(() -> service.create(student.getId(),new CourseDtos.Create("Forbidden",null))).isInstanceOf(AppException.class);
        var first = service.create(teacher.getId(),new CourseDtos.Create("One",null));
        var unit = service.addUnit(teacher.getId(),first.id(),new CourseDtos.UnitRequest("Lesson","Content"));
        var second = service.create(teacher.getId(),new CourseDtos.Create("Two",null));
        service.addUnit(teacher.getId(),second.id(),new CourseDtos.UnitRequest("Another","Content"));
        service.submitReview(teacher.getId(),second.id());service.review(admin.getId(),second.id(),new CourseDtos.Review(true,null));
        service.enroll(student.getId(),second.id());
        assertThatThrownBy(() -> service.complete(student.getId(),second.id(),unit.id())).isInstanceOf(AppException.class);
        assertThat(service.progress(student.getId(),second.id()).completedUnits()).isZero();
    }

    private AccountEntity account(AccountRole role) {
        var value = new AccountEntity();value.setId(UUID.randomUUID());value.setUsername("course_"+UUID.randomUUID().toString().replace("-",""));
        value.setEmail(value.getUsername()+"@example.test");value.setPasswordHash("unused");value.setRole(role);value.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(value);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor identity(AccountEntity account) {
        return jwt().jwt(token -> token.subject(account.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_"+account.getRole().name()));
    }
}
