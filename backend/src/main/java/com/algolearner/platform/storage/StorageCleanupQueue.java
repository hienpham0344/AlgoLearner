package com.algolearner.platform.storage;

import java.util.UUID;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable compensation for private avatar objects. */
@Component
@Profile("!mock")
public class StorageCleanupQueue {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;
    private final TransactionTemplate suspended;

    public StorageCleanupQueue(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.writes = new TransactionTemplate(manager);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writes.setTimeout(10);
        this.reads = new TransactionTemplate(manager);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reads.setReadOnly(true);
        reads.setTimeout(10);
        this.suspended = new TransactionTemplate(manager);
        suspended.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    /** Independent commit survives rollback of the upload/profile transaction. */
    public void enqueue(String key) {
        validateKey(key);
        writes.executeWithoutResult(status -> jdbc.update("""
                insert into tbl_storage_cleanup_job(id, object_key) values (?, ?)
                on conflict (object_key) do update
                    set status='PENDING', attempts=0, available_at=current_timestamp,
                        last_error_code=null, updated_at=current_timestamp
                    where tbl_storage_cleanup_job.status='CANCELLED'
                """, UUID.randomUUID(), key));
    }

    public boolean isCurrentAvatar(String key) {
        validateKey(key);
        return Boolean.TRUE.equals(reads.execute(status -> jdbc.queryForObject(
                "select exists(select 1 from tbl_account where avatar_key=?)", Boolean.class, key)));
    }

    <T> T withoutTransaction(Supplier<T> action) {
        return suspended.execute(status -> action.get());
    }

    Optional<Claim> claim(int maxAttempts, int leaseSeconds) {
        return writes.execute(status -> {
            // A crashed final attempt must reach the same visible DLQ as a provider failure.
            jdbc.update("""
                    with exhausted as (
                        select id from tbl_storage_cleanup_job
                        where status='PROCESSING' and lease_until<=current_timestamp and attempts>=?
                        order by lease_until, id for update skip locked limit 100
                    )
                    update tbl_storage_cleanup_job job set status='FAILED', lease_until=null,
                        lease_token=null, last_error_code='CLEANUP_LEASE_EXPIRED', updated_at=current_timestamp
                    from exhausted where job.id=exhausted.id
                    """, maxAttempts);
            var token = UUID.randomUUID();
            var claims = jdbc.query("""
                    with candidate as (
                        select id from tbl_storage_cleanup_job
                        where attempts<? and (
                            (status='PENDING' and available_at<=current_timestamp)
                            or (status='PROCESSING' and lease_until<=current_timestamp))
                        order by available_at, created_at, id for update skip locked limit 1
                    )
                    update tbl_storage_cleanup_job job set status='PROCESSING', attempts=attempts+1,
                        lease_until=current_timestamp+(? * interval '1 second'), lease_token=?,
                        updated_at=current_timestamp
                    from candidate where job.id=candidate.id
                    returning job.id, job.object_key, job.attempts
                    """, (row, index) -> new Claim(row.getObject(1, UUID.class), row.getString(2), row.getInt(3), token),
                    maxAttempts, leaseSeconds, token);
            return claims.stream().findFirst();
        });
    }

    boolean ownsLease(Claim claim) {
        return Boolean.TRUE.equals(reads.execute(status -> jdbc.queryForObject("""
                select exists(select 1 from tbl_storage_cleanup_job
                    where id=? and status='PROCESSING' and lease_token=? and lease_until>current_timestamp)
                """, Boolean.class, claim.id(), claim.token())));
    }

    void finish(Claim claim, boolean activeAvatar) {
        writes.executeWithoutResult(status -> jdbc.update("""
                update tbl_storage_cleanup_job set status=?, lease_until=null, lease_token=null,
                    last_error_code=?, updated_at=current_timestamp
                where id=? and status='PROCESSING' and lease_token=?
                """, activeAvatar ? "CANCELLED" : "COMPLETED", activeAvatar ? "ACTIVE_AVATAR" : null,
                claim.id(), claim.token()));
    }

    boolean fail(Claim claim, int maxAttempts, long delaySeconds) {
        boolean exhausted = claim.attempts() >= maxAttempts;
        writes.executeWithoutResult(status -> jdbc.update("""
                update tbl_storage_cleanup_job set status=?, lease_until=null, lease_token=null,
                    available_at=current_timestamp+(? * interval '1 second'),
                    last_error_code='PRIVATE_AVATAR_DELETE_FAILED', updated_at=current_timestamp
                where id=? and status='PROCESSING' and lease_token=?
                """, exhausted ? "FAILED" : "PENDING", delaySeconds, claim.id(), claim.token()));
        return exhausted;
    }

    record Claim(UUID id, String key, int attempts, UUID token) { }

    static void validateKey(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}/[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("Invalid private avatar object key");
        }
        for (String part : key.split("/")) {
            if (!UUID.fromString(part).toString().equals(part)) {
                throw new IllegalArgumentException("Invalid private avatar object key");
            }
        }
    }
}
