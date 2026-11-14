package com.algolearner.platform.profile;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JpaProfileRepositoryTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final JpaProfileRepository repository = new JpaProfileRepository(accounts);

    @Test
    void partialUpdatePreservesCurrentRowValuesForOmittedFields() {
        UUID accountId = UUID.randomUUID();
        var account = new AccountEntity();
        account.setId(accountId);
        account.setFullName("Previous name");
        account.setShortBio("A newer bio from another request");
        when(accounts.findForUpdateById(accountId)).thenReturn(Optional.of(account));

        repository.update(accountId, true, "Updated name", false, null, Instant.parse("2026-11-01T00:00:00Z"));

        assertThat(account.getFullName()).isEqualTo("Updated name");
        assertThat(account.getShortBio()).isEqualTo("A newer bio from another request");
        verify(accounts).findForUpdateById(accountId);
    }

    @Test
    void explicitNullClearsFieldWhileOmittedFieldIsPreserved() {
        UUID accountId = UUID.randomUUID();
        var account = new AccountEntity();
        account.setId(accountId);
        account.setFullName("Current name");
        account.setShortBio("Current bio");
        when(accounts.findForUpdateById(accountId)).thenReturn(Optional.of(account));

        repository.update(accountId, false, null, true, null, Instant.parse("2026-11-01T00:00:00Z"));

        assertThat(account.getFullName()).isEqualTo("Current name");
        assertThat(account.getShortBio()).isNull();
    }
}
