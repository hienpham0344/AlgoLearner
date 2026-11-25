package com.algolearner.platform.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Remote deletion never holds a database transaction or row lock. */
@Component
@Profile("!mock")
public class StorageCleanupWorker {
    private static final Logger LOG = LoggerFactory.getLogger(StorageCleanupWorker.class);
    private final StorageCleanupQueue queue;
    private final PrivateObjectStorage objects;
    private final int maxAttempts;
    private final int backoffSeconds;
    private final int leaseSeconds;

    public StorageCleanupWorker(StorageCleanupQueue queue, PrivateObjectStorage objects,
            int maxAttempts, int backoffSeconds) {
        this(queue, objects, maxAttempts, backoffSeconds, 120);
    }

    @Autowired
    public StorageCleanupWorker(StorageCleanupQueue queue, PrivateObjectStorage objects,
            @Value("${app.storage.avatar-cleanup-max-attempts:5}") int maxAttempts,
            @Value("${app.storage.avatar-cleanup-backoff-seconds:60}") int backoffSeconds,
            @Value("${app.storage.avatar-cleanup-lease-seconds:120}") int leaseSeconds) {
        if (maxAttempts < 1 || maxAttempts > 20 || backoffSeconds < 1 || backoffSeconds > 3600
                || leaseSeconds < 120 || leaseSeconds > 3600) {
            throw new IllegalArgumentException("Invalid avatar cleanup retry configuration");
        }
        this.queue = queue;
        this.objects = objects;
        this.maxAttempts = maxAttempts;
        this.backoffSeconds = backoffSeconds;
        this.leaseSeconds = leaseSeconds;
    }

    public boolean runOnce() {
        return queue.withoutTransaction(() -> {
            var next = queue.claim(maxAttempts, leaseSeconds);
            if (next.isEmpty()) return false;
            var claim = next.get();
            try {
                if (!queue.ownsLease(claim)) return true;
                if (queue.isCurrentAvatar(claim.key())) {
                    queue.finish(claim, true);
                    return true;
                }
                objects.delete(claim.key());
                queue.finish(claim, false);
            } catch (RuntimeException error) {
                long delay = Math.min(3600L, (long) backoffSeconds << Math.min(claim.attempts() - 1, 20));
                if (queue.fail(claim, maxAttempts, delay)) {
                    LOG.error("PRIVATE_AVATAR_CLEANUP_FAILED jobId={} attempts={}", claim.id(), claim.attempts());
                } else {
                    LOG.warn("PRIVATE_AVATAR_CLEANUP_RETRY jobId={} attempts={}", claim.id(), claim.attempts());
                }
            }
            return true;
        });
    }
}
