package com.algolearner.platform.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    Optional<AccountEntity> findByUsernameIgnoreCase(String username);
    Optional<AccountEntity> findByEmailIgnoreCase(String email);
}
