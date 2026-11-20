package com.algolearner.platform.admin;

import com.algolearner.platform.audit.AccountAuditEntity;
import com.algolearner.platform.audit.AccountAuditRepository;
import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.dto.PageResponse;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.algolearner.platform.admin.AccountAdminDtos.*;

@Service
@Profile("internal-auth")
@Transactional(readOnly = true)
public class AccountAdministrationService {
    private final AccountRepository accounts;
    private final AuthSessionService sessions;
    private final AccountAuditRepository audits;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public AccountAdministrationService(AccountRepository accounts, AuthSessionService sessions,
            AccountAuditRepository audits, PasswordEncoder passwords, Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.audits = audits;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional
    public PageResponse<AccountView> list(UUID actorId, Pageable pageable) {
        admin(actorId);
        releaseExpiredSuspensions();
        return PageResponse.fromPage(accounts.findAll(pageable).map(this::view));
    }

    @Transactional
    public AccountView suspend(UUID actorId, UUID targetId, SuspendRequest input) {
        var actor = admin(actorId);
        var target = target(targetId);
        if (target.getRole() == AccountRole.ADMIN || target.getStatus() != AccountStatus.ACTIVE)
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only active Student or Teacher accounts can be suspended.");
        String reason = input.reason().trim();
        if (reason.isEmpty() || input.endsAt() != null && !input.endsAt().isAfter(clock.instant()))
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        target.setStatus(AccountStatus.SUSPENDED);
        target.setSuspensionReason(reason);
        target.setSuspensionEndsAt(input.endsAt());
        sessions.revokeOtherSessions(targetId, null);
        audit(actor, target, "SUSPEND", reason);
        return view(target);
    }

    @Transactional
    public AccountView unsuspend(UUID actorId, UUID targetId) {
        var actor = admin(actorId);
        var target = target(targetId);
        if (target.getStatus() != AccountStatus.SUSPENDED)
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Account is not suspended.");
        target.setStatus(AccountStatus.ACTIVE);
        target.setSuspensionReason(null);
        target.setSuspensionEndsAt(null);
        audit(actor, target, "UNSUSPEND", null);
        return view(target);
    }

    @Transactional
    public AccountView reactivate(UUID actorId, UUID targetId) {
        var actor = admin(actorId);
        var target = target(targetId);
        if (target.getStatus() != AccountStatus.DEACTIVATED)
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Account is not deactivated.");
        target.setStatus(AccountStatus.ACTIVE);
        audit(actor, target, "REACTIVATE", null);
        return view(target);
    }

    @Transactional
    public void deactivate(UUID actorId, String currentPassword, boolean confirmed) {
        var target = target(actorId);
        if (!confirmed || target.getRole() == AccountRole.ADMIN || target.getStatus() != AccountStatus.ACTIVE
                || target.getPasswordHash() == null || !passwords.matches(currentPassword, target.getPasswordHash()))
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Password and confirmation are required.");
        target.setStatus(AccountStatus.DEACTIVATED);
        sessions.revokeOtherSessions(actorId, null);
        audit(target, target, "DEACTIVATE", null);
    }

    @Transactional
    public void releaseExpiredSuspension(UUID id) {
        var account = accounts.findForUpdateById(id).orElse(null);
        if (account != null) releaseIfExpired(account, clock.instant());
    }

    @Scheduled(fixedDelayString = "${app.auth.suspension-cleanup-ms:60000}")
    @Transactional
    public void releaseExpiredSuspensions() {
        Instant now = clock.instant();
        for (var account : accounts.findByStatusAndSuspensionEndsAtLessThanEqual(AccountStatus.SUSPENDED, now))
            releaseIfExpired(account, now);
    }

    private void releaseIfExpired(AccountEntity account, Instant now) {
        if (account.getStatus() == AccountStatus.SUSPENDED && account.getSuspensionEndsAt() != null
                && !account.getSuspensionEndsAt().isAfter(now)) {
            account.setStatus(AccountStatus.ACTIVE);
            account.setSuspensionReason(null);
            account.setSuspensionEndsAt(null);
            audit(null, account, "AUTO_UNSUSPEND", "Suspension expired");
        }
    }

    private AccountEntity admin(UUID actorId) {
        var actor = accounts.findById(actorId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN_OPERATION));
        if (actor.getStatus() != AccountStatus.ACTIVE || actor.getRole() != AccountRole.ADMIN)
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        return actor;
    }

    private AccountEntity target(UUID targetId) {
        return accounts.findForUpdateById(targetId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void audit(AccountEntity actor, AccountEntity target, String action, String reason) {
        var event = new AccountAuditEntity();
        event.setActor(actor);
        event.setTarget(target);
        event.setAction(action);
        event.setReason(reason);
        audits.save(event);
    }

    private AccountView view(AccountEntity account) {
        String status = account.getLoginLockedUntil() != null
                && account.getLoginLockedUntil().isAfter(clock.instant())
                && account.getStatus() == AccountStatus.ACTIVE
                ? "TEMPORARILY_LOCKED" : account.getStatus().name();
        return new AccountView(account.getId(), account.getUsername(), account.getEmail(), account.getRole(),
                status, account.getSuspensionReason(), account.getSuspensionEndsAt(),
                account.getLoginLockedUntil(), account.getCreatedAt());
    }
}
