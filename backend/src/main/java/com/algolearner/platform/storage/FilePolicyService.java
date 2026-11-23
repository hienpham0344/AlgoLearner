package com.algolearner.platform.storage;

import java.util.Set;
import java.util.UUID;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.stream.Collectors;
import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.common.security.AccountAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FilePolicyService {
    private final JdbcTemplate jdbc;
    private final AccountAccess accounts;
    private final AuditService audit;
    private final Clock clock;
    public record Policy(long maxBytes, Set<FileKind> allowedKinds) { }

    public Policy read(UUID actor) {
        requireAdmin(actor);
        return current(false);
    }

    @Transactional
    public Policy update(UUID actor, Policy policy, String reason) {
        requireAdmin(actor);
        if (policy == null || policy.maxBytes() < 1 || policy.maxBytes() > 20L * 1024 * 1024
                || policy.allowedKinds() == null || policy.allowedKinds().isEmpty() || policy.allowedKinds().stream().anyMatch(java.util.Objects::isNull)
                || reason == null || reason.isBlank() || reason.strip().length() > 500) {
            throw new StorageException("INVALID_FILE_POLICY", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Upload policy requires a valid limit, allowed types and reason");
        }
        var before = current(true);
        String kinds = policy.allowedKinds().stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        jdbc.update("UPDATE tbl_file_policy SET max_bytes=?,allowed_kinds=?,updated_at=? WHERE id='00000000-0000-0000-0000-000000000001'",
                policy.maxBytes(), kinds, Timestamp.from(clock.instant()));
        if (!before.equals(policy)) {
            audit.record(actor, "ADMIN", "FILE_POLICY", "GLOBAL", "FILE_POLICY_UPDATED",
                    before.toString(), policy.toString(), reason.strip(), null);
        }
        return new Policy(policy.maxBytes(), Set.copyOf(policy.allowedKinds()));
    }

    private Policy current(boolean lock) {
        return jdbc.queryForObject("SELECT max_bytes,allowed_kinds FROM tbl_file_policy WHERE id='00000000-0000-0000-0000-000000000001'"
                        + (lock ? " FOR UPDATE" : ""),
                (row, index) -> new Policy(row.getLong("max_bytes"), Arrays.stream(row.getString("allowed_kinds").split(","))
                        .map(FileKind::valueOf).collect(Collectors.toUnmodifiableSet())));
    }

    private void requireAdmin(UUID actor) {
        if (!accounts.activeRole(actor).equals("ADMIN")) {
            throw new StorageException("FORBIDDEN_OPERATION", HttpStatus.FORBIDDEN, "Only Admin may manage upload policy");
        }
    }
}
