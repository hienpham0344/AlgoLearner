package com.algolearner.platform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Creates the fixed administrator from deployment configuration, never from public signup. */
@Component
@Profile("!mock")
public class AdminAccountBootstrap implements ApplicationRunner {
    private final AccountRepository accounts;
    private final Clock clock;
    private final String email;
    private final String username;
    private final String passwordHash;

    public AdminAccountBootstrap(AccountRepository accounts, Clock clock,
            @Value("${app.auth.admin.email:}") String email,
            @Value("${app.auth.admin.username:}") String username,
            @Value("${app.auth.admin.password-hash:}") String passwordHash) {
        this.accounts = accounts;
        this.clock = clock;
        this.email = email.strip().toLowerCase();
        this.username = username.strip();
        this.passwordHash = passwordHash.strip();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() && username.isEmpty() && passwordHash.isEmpty()) return;
        if (email.isEmpty() || username.isEmpty() || !passwordHash.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$")) {
            throw new IllegalStateException("ADMIN_EMAIL, ADMIN_USERNAME and a BCrypt ADMIN_PASSWORD_HASH are required together");
        }
        var existingAccount = accounts.findByEmailIgnoreCase(email);
        if (existingAccount.isPresent()) {
            AccountEntity existing = existingAccount.orElseThrow();
            if (existing.getRole() != AccountRole.ADMIN || !existing.getUsername().equals(username)) {
                throw new IllegalStateException("Configured admin email belongs to another account");
            }
            return;
        }
        if (accounts.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("Configured admin username belongs to another account");
        }
        accounts.save(AccountEntity.builder()
                .id(UUID.randomUUID()).email(email).username(username)
                .passwordHash(passwordHash).role(AccountRole.ADMIN).status(AccountStatus.ACTIVE)
                .emailVerifiedAt(clock.instant()).build());
    }
}
