package com.algolearner.platform.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!mock & !test")
public class StorageCleanupScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(StorageCleanupScheduler.class);
    private final StorageCleanupWorker worker;
    private final int batchSize;

    public StorageCleanupScheduler(StorageCleanupWorker worker,
            @Value("${app.storage.avatar-cleanup-batch-size:20}") int batchSize) {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("Invalid avatar cleanup batch size");
        this.worker = worker;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.storage.avatar-cleanup-poll-ms:30000}")
    public void clean() {
        try {
            for (int completed = 0; completed < batchSize && worker.runOnce(); completed++) { }
        } catch (RuntimeException failure) {
            LOG.error("PRIVATE_AVATAR_CLEANUP_QUEUE_UNAVAILABLE");
        }
    }
}
