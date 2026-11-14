package com.algolearner.platform.auth;

import com.algolearner.platform.common.exception.ApiException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final JwtEncoder tokens = mock(JwtEncoder.class);
    private final AuthSessionService service = new AuthSessionService(sessions, accounts, tokens, clock, "test-issuer");

    @Test
    void listReturnsOnlyLiveOwnSessionsAndMarksCurrent() {
        UUID ownerId = UUID.randomUUID();
        var current = session(ownerId, clock.instant().plusSeconds(3600));
        current.setUserAgent("Browser A");
        var expired = session(ownerId, clock.instant().minusSeconds(1));
        when(sessions.findByAccountIdAndRevokedAtIsNull(ownerId)).thenReturn(List.of(current, expired));
        var listed = service.list(ownerId, current.getId());
        assertThat(listed).hasSize(1);
        assertThat(listed.getFirst().current()).isTrue();
        assertThat(listed.getFirst().userAgent()).isEqualTo("Browser A");
    }

    @Test
    void revokeRejectsForeignSessionAndPreservesCurrent() {
        UUID ownerId = UUID.randomUUID();
        var owner = new AccountEntity(); owner.setId(ownerId);
        UUID currentId = UUID.randomUUID();
        var foreign = session(UUID.randomUUID(), clock.instant().plusSeconds(3600));
        when(accounts.findForUpdateById(ownerId)).thenReturn(Optional.of(owner));
        when(sessions.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.revoke(ownerId, currentId, foreign.getId()))
                .isInstanceOf(ApiException.class);
        assertThat(foreign.getRevokedAt()).isNull();
        assertThatThrownBy(() -> service.revoke(ownerId, currentId, currentId))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void logoutCurrentRevokesOnlySessionOwnedByBearerAccount() {
        UUID ownerId = UUID.randomUUID();
        var owner = new AccountEntity(); owner.setId(ownerId);
        var own = session(ownerId, clock.instant().plusSeconds(3600));
        var foreign = session(UUID.randomUUID(), clock.instant().plusSeconds(3600));
        when(accounts.findForUpdateById(ownerId)).thenReturn(Optional.of(owner));
        when(sessions.findById(own.getId())).thenReturn(Optional.of(own));
        when(sessions.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        service.logoutCurrent(ownerId, foreign.getId());
        assertThat(foreign.getRevokedAt()).isNull();
        service.logoutCurrent(ownerId, own.getId());
        assertThat(own.getRevokedAt()).isEqualTo(clock.instant());
    }

    private AuthSessionEntity session(UUID accountId, Instant expiresAt) {
        var account = new AccountEntity(); account.setId(accountId);
        var session = new AuthSessionEntity();
        session.setId(UUID.randomUUID());
        session.setAccount(account);
        session.setExpiresAt(expiresAt);
        session.setCreatedAt(clock.instant());
        return session;
    }
}
