package com.algolearner.platform.storage;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "debug=false",
        "app.security.jwt-secret=file-policy-test-only-secret-at-least-32-bytes"})
@ActiveProfiles({"internal-auth", "test"})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
@Transactional
class FilePolicyServiceTest extends PostgresIntegrationTest {
    @Autowired FilePolicyService service;
    @Autowired AccountRepository accounts;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void adminPolicyChangesAreValidatedAndAudited() {
        var admin = account(AccountRole.ADMIN);
        var result = service.update(admin.getId(),new FilePolicyService.Policy(1024, Set.of(FileKind.PDF)),"Restrict classroom uploads");
        assertThat(result).isEqualTo(service.read(admin.getId()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tbl_audit_log WHERE actor_id=? AND action='FILE_POLICY_UPDATED'",Integer.class,admin.getId())).isEqualTo(1);
        assertThatThrownBy(() -> service.update(admin.getId(),new FilePolicyService.Policy(21L*1024*1024,Set.of(FileKind.PDF)),"Too large"))
                .isInstanceOf(AppException.class);
        assertThat(service.read(admin.getId()).maxBytes()).isEqualTo(1024);
    }

    @Test
    void teacherCannotChangeGlobalFilePolicy() {
        var teacher = account(AccountRole.TEACHER);
        assertThatThrownBy(() -> service.update(teacher.getId(),new FilePolicyService.Policy(1024,Set.of(FileKind.PDF)),"Attempt"))
                .isInstanceOf(AppException.class);
    }

    private AccountEntity account(AccountRole role) {
        var value = new AccountEntity(); value.setId(UUID.randomUUID());
        value.setUsername("policy_"+UUID.randomUUID().toString().replace("-",""));
        value.setEmail(value.getUsername()+"@example.test");value.setPasswordHash("unused");
        value.setRole(role);value.setStatus(AccountStatus.ACTIVE);
        return accounts.saveAndFlush(value);
    }
}
