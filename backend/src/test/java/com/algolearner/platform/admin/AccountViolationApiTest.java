package com.algolearner.platform.admin;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties={"logging.level.root=WARN","app.security.jwt-secret=violation-persistence-test-only-secret-at-least-32-bytes","app.mail.from=sender@example.test"})
@AutoConfigureMockMvc
@ActiveProfiles({"internal-auth","test"})
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
class AccountViolationApiTest extends PostgresIntegrationTest {
 @Autowired MockMvc mvc; @Autowired AccountRepository accounts;
 @Autowired AccountViolationService service;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Autowired com.algolearner.platform.profile.dashboard.JdbcDashboardRepository dashboard;
 @Test void adminConfirmsAndRevokesViolationWhileStudentIsDenied() throws Exception {
  UUID admin=account(AccountRole.ADMIN),student=account(AccountRole.STUDENT),key=UUID.randomUUID();
  String path="/api/v1/admin/accounts/"+student+"/violations";
  var context=new com.algolearner.platform.profile.dashboard.DashboardRepository.Context(admin,java.time.Instant.now(),java.time.Instant.now().plusSeconds(60),5);
  long before=dashboard.moderation(context).confirmedViolationAccountCount();
  org.assertj.core.api.Assertions.assertThatThrownBy(()->service.confirm(student,student,UUID.randomUUID(),"Invalid actor"))
      .isInstanceOf(com.algolearner.platform.common.exception.AppException.class);
  mvc.perform(post(path).with(user(student.toString()).roles("STUDENT")).contentType("application/json")
      .content("{\"requestId\":\""+key+"\",\"reason\":\"Verified evidence\"}")).andExpect(status().isForbidden());
  var result=mvc.perform(post(path).with(user(admin.toString()).roles("ADMIN")).contentType("application/json")
      .content("{\"requestId\":\""+key+"\",\"reason\":\"Verified evidence\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data.violationId").exists()).andReturn();
  String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data").path("violationId").asText();
  mvc.perform(post(path).with(user(admin.toString()).roles("ADMIN")).contentType("application/json")
      .content("{\"requestId\":\""+key+"\",\"reason\":\"Verified evidence\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data.violationId").value(id));
  org.assertj.core.api.Assertions.assertThat(dashboard.moderation(context).confirmedViolationAccountCount()).isEqualTo(before+1);
  org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tbl_audit_log WHERE target_id=? AND action='VIOLATION_CONFIRMED'",Long.class,student.toString())).isEqualTo(1);
  mvc.perform(post(path+"/"+id+"/revoke").with(user(admin.toString()).roles("ADMIN")).contentType("application/json")
      .content("{\"reason\":\"Evidence invalidated\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data.revokedAt").isNotEmpty());
  org.assertj.core.api.Assertions.assertThat(dashboard.moderation(context).confirmedViolationAccountCount()).isEqualTo(before);
  service.revoke(admin,student,UUID.fromString(id),"Repeat revoke");
  org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tbl_audit_log WHERE target_id=? AND action='VIOLATION_REVOKED'",Long.class,student.toString())).isEqualTo(1);
 }
 private UUID account(AccountRole role){var a=new AccountEntity();a.setId(UUID.randomUUID());a.setUsername("violation-"+a.getId());a.setEmail(a.getUsername()+"@example.test");a.setPasswordHash("unused-test-fixture");a.setRole(role);a.setStatus(AccountStatus.ACTIVE);return accounts.saveAndFlush(a).getId();}
}
