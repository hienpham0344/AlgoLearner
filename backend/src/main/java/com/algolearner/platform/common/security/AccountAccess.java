package com.algolearner.platform.common.security;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.UUID;
@Service
public class AccountAccess {
    private final AccountAccessRepository repository;
    private final Clock clock;
    public AccountAccess(AccountAccessRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }
    public String activeRole(UUID id) {
        return repository.activeRole(id, clock.instant()).orElseThrow(() ->
                new ApiException(ErrorCode.FORBIDDEN_OPERATION,"Account access denied"));
    }
}
