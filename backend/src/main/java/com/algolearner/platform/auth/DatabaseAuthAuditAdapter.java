package com.algolearner.platform.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@Profile("!mock")
public class DatabaseAuthAuditAdapter implements AuthAuditPort {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAuthAuditAdapter.class);
    private final AuditLogRepository auditLogRepository;
    private final AccountRepository accountRepository;

    public DatabaseAuthAuditAdapter(AuditLogRepository auditLogRepository, AccountRepository accountRepository) {
        this.auditLogRepository = auditLogRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void record(UUID actorId, UUID targetId, AuditActorType actorType, String action, AuditOutcome outcome, Instant occurredAt) {
        try {
            AccountEntity actor = actorId != null ? accountRepository.findById(actorId).orElse(null) : null;
            AccountEntity target = targetId != null ? accountRepository.findById(targetId).orElse(null) : null;

            AuditLogEntity logEntry = AuditLogEntity.builder()
                    .id(UUID.randomUUID())
                    .actorAccount(actor)
                    .targetAccount(target)
                    .actorType(actorType)
                    .action(action)
                    .outcome(outcome)
                    .occurredAt(occurredAt)
                    .build();

            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to record audit log: action={}, actor={}, target={}, error={}", action, actorId, targetId, e.getMessage());
        }
    }
}
