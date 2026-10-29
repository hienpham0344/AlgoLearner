package com.algolearner.platform.common.security;
import com.algolearner.platform.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.UUID;
@Service
public class AccountAccess {
    private final AccountRepository repository;
    private final Clock clock;
    public AccountAccess(AccountRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }
    public String activeRole(UUID id) {
        return repository.accounts().stream().filter(a -> a.id().equals(id) && a.status().equals("ACTIVE"))
            .filter(a -> repository.restrictions().stream().noneMatch(r -> r.accountId().equals(id) && r.active(clock.instant())))
            .map(AccountRepository.Account::role).findFirst().orElseThrow(() ->
                new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION","Account access denied"));
    }
}
