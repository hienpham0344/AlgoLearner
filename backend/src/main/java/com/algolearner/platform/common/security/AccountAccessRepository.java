package com.algolearner.platform.common.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Account state lookup used for every authenticated request. */
public interface AccountAccessRepository {
    Optional<String> activeRole(UUID accountId, Instant now);
}
