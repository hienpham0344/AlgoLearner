package com.algolearner.platform.profile;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.exception.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@org.springframework.context.annotation.Profile("internal-auth")
@Transactional(readOnly = true)
public class JpaProfileRepository implements ProfileRepository {
    private final AccountRepository accounts;
    public JpaProfileRepository(AccountRepository accounts) { this.accounts = accounts; }

    @Override public Profile own(UUID id) { return snapshot(account(id)); }
    @Override public Profile byUsername(String username) {
        return snapshot(accounts.findByUsernameIgnoreCase(username).orElseThrow(this::missing));
    }
    @Override public Profile publicProfile(String username, Instant now) {
        var account = accounts.findByUsernameIgnoreCase(username).orElseThrow(this::missing);
        if (!account.isProfilePublic() || account.getStatus() != AccountStatus.ACTIVE) throw missing();
        return snapshot(account);
    }
    @Override @Transactional public void update(UUID id, boolean updateName, String name, boolean updateBio, String bio, Instant now) {
        var account = accountForUpdate(id);
        if (updateName) account.setFullName(name);
        if (updateBio) account.setShortBio(bio);
    }
    @Override @Transactional public void privacy(UUID id, boolean visible, Instant now) {
        accountForUpdate(id).setProfilePublic(visible);
    }
    @Override @Transactional public void avatar(UUID id, String key, Instant now) {
        accountForUpdate(id).setAvatarKey(key);
    }
    private AccountEntity account(UUID id) { return accounts.findById(id).orElseThrow(this::missing); }
    private AccountEntity accountForUpdate(UUID id) { return accounts.findForUpdateById(id).orElseThrow(this::missing); }
    private Profile snapshot(AccountEntity account) {
        if (account.getRole() == AccountRole.ADMIN) throw missing();
        return new Profile(account.getId(), account.getFullName(), account.getUsername(), account.getEmail(),
                account.getAvatarKey(), account.getShortBio(), account.getRole().name(), account.getCreatedAt(), account.isProfilePublic());
    }
    private ApiException missing() { return new ApiException(ErrorCode.PROFILE_NOT_FOUND); }
}
