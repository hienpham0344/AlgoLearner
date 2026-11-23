package com.algolearner.platform.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.algolearner.platform.common.exception.AppException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountIdentityRegressionTest {
    @Test
    void ambiguousLoginRejectsBeforePasswordOrFailureCounterMutation() {
        var accounts = mock(AccountRepository.class);
        var passwords = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        var emailOwner = account("shared@example.test", "owner");
        var usernameOwner = account("other@example.test", "shared@example.test");
        when(accounts.findForLoginByEmail("shared@example.test")).thenReturn(Optional.of(emailOwner));
        when(accounts.findForLoginByUsername("shared@example.test")).thenReturn(Optional.of(usernameOwner));
        when(accounts.findForIdentityResolution("shared@example.test")).thenReturn(java.util.List.of(emailOwner, usernameOwner));
        var service = new AuthService(accounts, mock(RegistrationOtpRepository.class), mock(RegistrationMailer.class),
                passwords, mock(OtpCodeHasher.class), mock(AuthSessionService.class),
                mock(com.algolearner.platform.admin.AccountAdministrationService.class), Clock.systemUTC());
        var failure = assertThrows(AppException.class,
                () -> service.login(new AuthDtos.LoginRequest("shared@example.test", "wrong", false)));
        assertEquals("AMBIGUOUS_IDENTITY", failure.getErrorCode());
        assertEquals(0, emailOwner.getFailedLoginAttempts());
        assertEquals(0, usernameOwner.getFailedLoginAttempts());
        verifyNoInteractions(passwords);
    }

    @Test
    void explicitNamespaceResolvesLegacyUsernameAndEmailOwners() {
        var accounts = mock(AccountRepository.class);
        var emailOwner = account("shared@example.test", "owner");
        var usernameOwner = account("other@example.test", "shared@example.test");
        when(accounts.findForLoginByEmail("shared@example.test")).thenReturn(Optional.of(emailOwner));
        when(accounts.findForLoginByUsername("shared@example.test")).thenReturn(Optional.of(usernameOwner));
        var resolver = new AccountIdentityResolver(accounts);
        assertSame(emailOwner, resolver.resolve("shared@example.test", IdentityType.EMAIL).orElseThrow());
        assertSame(usernameOwner, resolver.resolve("shared@example.test", IdentityType.USERNAME).orElseThrow());
    }

    @Test
    void sameAccountInBothNamespacesIsUnambiguousAndCaseInsensitiveLookupIsPreserved() {
        var accounts = mock(AccountRepository.class);
        var owner = account("shared@example.test", "shared@example.test");
        when(accounts.findForLoginByEmail("SHARED@example.test")).thenReturn(Optional.of(owner));
        when(accounts.findForLoginByUsername("SHARED@example.test")).thenReturn(Optional.of(owner));
        when(accounts.findForIdentityResolution("SHARED@example.test")).thenReturn(java.util.List.of(owner));
        assertSame(owner, new AccountIdentityResolver(accounts).resolve(" SHARED@example.test ", null).orElseThrow());
    }

    @Test
    void ambiguousRecoveryAndOtpNeverTouchOtpRepository() {
        var accounts = mock(AccountRepository.class);
        var otps = mock(RegistrationOtpRepository.class);
        when(accounts.findForLoginByEmail("shared@example.test")).thenReturn(Optional.of(account("shared@example.test", "owner")));
        when(accounts.findForLoginByUsername("shared@example.test")).thenReturn(Optional.of(account("other@example.test", "shared@example.test")));
        var first = accounts.findForLoginByEmail("shared@example.test").orElseThrow();
        var second = accounts.findForLoginByUsername("shared@example.test").orElseThrow();
        when(accounts.findForIdentityResolution("shared@example.test")).thenReturn(java.util.List.of(first, second));
        var service = new AuthService(accounts, otps, mock(RegistrationMailer.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class), mock(OtpCodeHasher.class),
                mock(AuthSessionService.class), mock(com.algolearner.platform.admin.AccountAdministrationService.class), Clock.systemUTC());
        assertThrows(AccountIdentityResolver.AmbiguousIdentityException.class,
                () -> service.forgotPassword(new AuthDtos.ForgotPasswordRequest(null, "shared@example.test")));
        assertThrows(AccountIdentityResolver.AmbiguousIdentityException.class,
                () -> service.verifyReset(new AuthDtos.VerifyOtpRequest(null, "shared@example.test", "123456", "RESET_PASSWORD")));
        assertThrows(AccountIdentityResolver.AmbiguousIdentityException.class,
                () -> service.resetPassword(new AuthDtos.ResetPasswordRequest(null, "shared@example.test", "grant", "Password123!", "Password123!")));
        assertThrows(AccountIdentityResolver.AmbiguousIdentityException.class,
                () -> service.resend(new AuthDtos.EmailRequest(null, "shared@example.test", "RESET_PASSWORD")));
        verifyNoInteractions(otps);
    }

    @Test
    void emailFieldAlwaysSelectsEmailNamespaceAndConstructorsRemainCompatible() {
        assertEquals(IdentityType.EMAIL, new AuthDtos.ForgotPasswordRequest("email@example.test").namespace());
        assertEquals(IdentityType.EMAIL, new AuthDtos.VerifyOtpRequest("email@example.test", null, "123456", "REGISTER", IdentityType.USERNAME).namespace());
        assertEquals(IdentityType.AUTO, new AuthDtos.ForgotPasswordRequest(null, "legacy").namespace());
    }

    @Test
    void newUsernameValidationAndCanonicalLockOrderPreventNamespaceCollisions() {
        assertThrows(AppException.class, () -> AccountIdentityRegistration.validateUsername("email@example.test"));
        assertThrows(AppException.class, () -> AccountIdentityRegistration.validateUsername("name\u0001"));
        assertThrows(AppException.class, () -> AccountIdentityRegistration.validateUsername("\u0001abc"));
        assertThrows(AppException.class, () -> AccountIdentityRegistration.validateUsername("  ab  "));
        assertThrows(AppException.class, () -> AccountIdentityRegistration.validateUsername("ab"));
        assertThrows(AppException.class, () -> AccountIdentityRegistration.validateUsername("   "));
        assertThrows(AppException.class, () -> AccountIdentityRegistration.validateUsername("a".repeat(51)));
        assertThrows(AppException.class, () -> AccountIdentityRegistration.validateUsername(null));
        assertDoesNotThrow(() -> AccountIdentityRegistration.validateUsername("  abc  "));
        var accounts = mock(AccountRepository.class);
        AccountIdentityRegistration.lock(accounts, "Z@EXAMPLE.TEST", "Alpha");
        var order = inOrder(accounts);
        order.verify(accounts).lockIdentity("alpha");
        order.verify(accounts).lockIdentity("z@example.test");
        when(accounts.existsConflictingIdentity("z@example.test", "alpha")).thenReturn(true);
        assertTrue(AccountIdentityRegistration.conflicts(accounts, "Z@EXAMPLE.TEST", "Alpha"));
        verify(accounts).existsConflictingIdentity("z@example.test", "alpha");
        verify(accounts, never()).existsByEmailIgnoreCase(any());
        verify(accounts, never()).existsByUsernameIgnoreCase(any());
    }

    private AccountEntity account(String email, String username) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID()); account.setEmail(email); account.setUsername(username);
        account.setStatus(AccountStatus.ACTIVE); account.setRole(AccountRole.STUDENT);
        account.setPasswordHash("hash");
        return account;
    }
}
