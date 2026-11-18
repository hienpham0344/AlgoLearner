package com.algolearner.platform.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!mock & !test")
@RequiredArgsConstructor
public class FileMaintenanceScheduler {
    private final FileMaintenanceService maintenance;

    @Scheduled(fixedDelayString = "${app.files.recovery-poll-ms:30000}")
    public void recover() { maintenance.recoverPending(); }

    @Scheduled(fixedDelayString = "${app.files.cleanup-poll-ms:3600000}")
    public void cleanup() { maintenance.cleanOrphans(); }
}
