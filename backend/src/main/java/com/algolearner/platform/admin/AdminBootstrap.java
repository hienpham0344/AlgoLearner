package com.algolearner.platform.admin;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountIdentityRegistration;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.auth.AccountRole;
import com.algolearner.platform.auth.AccountStatus;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Provision one fixed Admin from deployment configuration; public registration never creates Admin. */
@Component
@Profile("internal-auth")
public class AdminBootstrap implements ApplicationRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final String email;
    private final String username;
    private final String password;

    public AdminBootstrap(AccountRepository accounts, PasswordEncoder passwords,
            @Value("${app.security.admin-bootstrap.email:}") String email,
            @Value("${app.security.admin-bootstrap.username:}") String username,
            @Value("${app.security.admin-bootstrap.password:}") String password) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.email = email;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && username.isBlank() && password.isBlank()) return;
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedUsername = username.trim();
        if (normalizedEmail.isBlank() || normalizedUsername.isBlank() || password.isBlank()
                || !normalizedEmail.contains("@") || username.codePoints().anyMatch(Character::isISOControl)
                || normalizedUsername.length() < 3 || normalizedUsername.length() > 50
                || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9]).*")) {
            throw new IllegalStateException("Admin bootstrap configuration is incomplete or invalid.");
        }
        AccountIdentityRegistration.lock(accounts, normalizedEmail, normalizedUsername);
        var byEmail = accounts.findByEmailIgnoreCase(normalizedEmail);
        var byUsername = accounts.findByUsernameIgnoreCase(normalizedUsername);
        var emailAsUsername = accounts.findByUsernameIgnoreCase(normalizedEmail);
        var usernameAsEmail = accounts.findByEmailIgnoreCase(normalizedUsername);
        if (java.util.stream.Stream.of(emailAsUsername, usernameAsEmail).flatMap(java.util.Optional::stream)
                .anyMatch(account -> byEmail.isEmpty() || !account.getId().equals(byEmail.get().getId())))
            throw new IllegalStateException("Admin bootstrap identity conflicts with an existing account.");
        if (byEmail.isPresent() || byUsername.isPresent()) {
            if (byEmail.isEmpty() || byUsername.isEmpty()
                    || !byEmail.get().getId().equals(byUsername.get().getId())
                    || byEmail.get().getRole() != AccountRole.ADMIN
                    || byEmail.get().getStatus() != AccountStatus.ACTIVE) {
                throw new IllegalStateException("Admin bootstrap identity conflicts with an existing account.");
            }
            return;
        }
        AccountIdentityRegistration.validateUsername(username);
        var admin = new AccountEntity();
        admin.setId(UUID.randomUUID());
        admin.setEmail(normalizedEmail);
        admin.setUsername(normalizedUsername);
        admin.setRole(AccountRole.ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        admin.setPasswordHash(passwords.encode(password));
        accounts.saveAndFlush(admin);
    }
}
