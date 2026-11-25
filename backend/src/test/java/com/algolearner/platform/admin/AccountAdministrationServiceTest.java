package com.algolearner.platform.admin;

import com.algolearner.platform.audit.AccountAuditRepository;
import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.exception.ApiException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountAdministrationServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final AccountAuditRepository audits = mock(AccountAuditRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AccountAdministrationService service =
            new AccountAdministrationService(accounts, sessions, audits, passwords, clock);

    @Test
    void suspensionRequiresAdminAndRevokesTargetSessions() {
        var admin = account(AccountRole.ADMIN);
        var target = account(AccountRole.STUDENT);
        when(accounts.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(accounts.findForUpdateById(target.getId())).thenReturn(Optional.of(target));
        var result = service.suspend(admin.getId(), target.getId(),
                new AccountAdminDtos.SuspendRequest("  Review  ", clock.instant().plusSeconds(3600)));
        assertThat(result.status()).isEqualTo("SUSPENDED");
        assertThat(target.getSuspensionReason()).isEqualTo("Review");
        verify(sessions).revokeOtherSessions(target.getId(), null);
        verify(audits).save(argThat(event -> event.getActor() == admin && event.getTarget() == target
                && event.getAction().equals("SUSPEND")));
    }

    @Test
    void wrongPasswordCannotDeactivateOrRevokeSessions() {
        var target = account(AccountRole.TEACHER);
        target.setPasswordHash("hash");
        when(accounts.findForUpdateById(target.getId())).thenReturn(Optional.of(target));
        when(passwords.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.deactivate(target.getId(), "wrong", true))
                .isInstanceOf(ApiException.class);
        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verifyNoInteractions(sessions, audits);
    }

    @Test
    void expiredSuspensionIsReleasedAndAudited() {
        var target = account(AccountRole.STUDENT);
        target.setStatus(AccountStatus.SUSPENDED);
        target.setSuspensionReason("Review");
        target.setSuspensionEndsAt(clock.instant().minusSeconds(1));
        when(accounts.findForUpdateById(target.getId())).thenReturn(Optional.of(target));
        service.releaseExpiredSuspension(target.getId());
        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(target.getSuspensionReason()).isNull();
        verify(audits).save(argThat(event -> event.getActor() == null
                && event.getAction().equals("AUTO_UNSUSPEND")));
    }

    private AccountEntity account(AccountRole role) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }
}
