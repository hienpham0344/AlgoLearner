package com.algolearner.platform.admin;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);

    @Test
    void createsOnlyConfiguredAdminWithHashedPassword() {
        when(passwords.encode("AdminTestPassword123!")).thenReturn("encoded");
        new AdminBootstrap(accounts, passwords, "Admin@Example.test", "sysadmin", "AdminTestPassword123!")
                .run(null);
        verify(accounts).saveAndFlush(argThat(account -> account.getRole() == AccountRole.ADMIN
                && account.getStatus() == AccountStatus.ACTIVE
                && account.getEmail().equals("admin@example.test")
                && account.getPasswordHash().equals("encoded")));
    }

    @Test
    void existingAdminIsNeverResetOnRestart() {
        var admin = new AccountEntity();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@example.test");
        admin.setUsername("sysadmin");
        admin.setRole(AccountRole.ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        when(accounts.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(admin));
        when(accounts.findByUsernameIgnoreCase("sysadmin")).thenReturn(Optional.of(admin));
        new AdminBootstrap(accounts, passwords, "admin@example.test", "sysadmin", "AdminTestPassword123!")
                .run(null);
        verify(accounts, never()).saveAndFlush(any());
        verifyNoInteractions(passwords);
    }

    @Test
    void incompleteConfigurationOrIdentityConflictFailsClosed() {
        assertThatThrownBy(() -> new AdminBootstrap(accounts, passwords,
                "admin@example.test", "", "AdminTestPassword123!").run(null))
                .isInstanceOf(IllegalStateException.class);
        var other = new AccountEntity();
        other.setId(UUID.randomUUID());
        when(accounts.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> new AdminBootstrap(accounts, passwords,
                "admin@example.test", "sysadmin", "AdminTestPassword123!").run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(accounts, never()).saveAndFlush(any());
    }
}
