package com.carddemo.migration;

import com.carddemo.config.CardDemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the COBOL fixture migration on startup when the database is still empty, so a fresh
 * PostgreSQL instance comes up with the complete legacy dataset already loaded.
 */
@Component
public class MigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final CardDemoProperties properties;
    private final CobolDataMigrationService migrationService;
    private final PendingAuthMigrationService pendingAuthMigrationService;

    public MigrationRunner(CardDemoProperties properties, CobolDataMigrationService migrationService,
                           PendingAuthMigrationService pendingAuthMigrationService) {
        this.properties = properties;
        this.migrationService = migrationService;
        this.pendingAuthMigrationService = pendingAuthMigrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getMigration().isEnabled()) {
            log.info("COBOL data migration disabled by configuration");
            return;
        }
        boolean force = properties.getMigration().isForce() || args.containsOption("migrate");

        // The pending authorization module arrived after the core data set was already loaded, so
        // its own load runs ahead of the core emptiness check. It is self-guarding: once
        // pending_auth_summary holds rows the call is a no-op.
        pendingAuthMigrationService.load();

        if (!force && !migrationService.isDatabaseEmpty()) {
            log.info("COBOL data already present; skipping migration. Counts: {}", migrationService.counts());
            return;
        }
        log.info("Migrating COBOL data sets into PostgreSQL...");
        migrationService.migrate();
        log.info("Migration complete. Counts: {}", migrationService.counts());
    }
}
