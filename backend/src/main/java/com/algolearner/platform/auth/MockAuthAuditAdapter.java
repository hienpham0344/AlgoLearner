package com.algolearner.platform.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@Profile("mock")
public class MockAuthAuditAdapter implements AuthAuditPort {

    private static final Logger log = LoggerFactory.getLogger(MockAuthAuditAdapter.class);

    @Override
    public void record(UUID actorId, UUID targetId, AuditActorType actorType, String action, AuditOutcome outcome, Instant occurredAt) {
        log.info("[MOCK AUDIT] Actor: {} | Target: {} | Action: {} | Outcome: {} | Time: {}",
                actorId, targetId, action, outcome, occurredAt);
    }
}

