package com.algolearner.platform.profile.avatar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Operator opt-in: --app.storage.avatar-migration-enabled=true --app.storage.avatar-migration-batch-size=100. */
@Component
@Profile("!mock")
@ConditionalOnProperty(name="app.storage.avatar-migration-enabled",havingValue="true")
@lombok.extern.slf4j.Slf4j
public class AvatarMigrationRunner implements ApplicationRunner {
    private final AvatarMigrationService migration;
    private final int batchSize;
    public AvatarMigrationRunner(AvatarMigrationService migration,@Value("${app.storage.avatar-migration-batch-size:100}") int batchSize) {
        this.migration=migration;this.batchSize=batchSize;
    }
    @Override public void run(ApplicationArguments arguments) {
        log.info("AVATAR_MIGRATION_BATCH completed={}",migration.migrateBatch(batchSize));
    }
}
