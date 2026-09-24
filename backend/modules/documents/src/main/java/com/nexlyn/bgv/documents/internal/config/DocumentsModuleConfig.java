package com.nexlyn.bgv.documents.internal.config;

import com.nexlyn.bgv.documents.internal.storage.StorageProperties;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import javax.sql.DataSource;

/**
 * Runs this module's own Flyway history against its own schema, independent of
 * every other module's migrations (see CLAUDE.md {@literal §4.2} rule 5: own schema, own migrations),
 * and reads the file-store and upload settings.
 */
@Configuration
@EnableConfigurationProperties({StorageProperties.class, UploadProperties.class})
public class DocumentsModuleConfig {

    @Bean(initMethod = "migrate")
    public Flyway documentsFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("documents")
                .locations("classpath:db/migration/documents")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
    }

    /** Prod refuses to start without a bucket: files must never silently go nowhere. */
    @Bean
    public StorageSettingsCheck storageSettingsCheck(StorageProperties storage, Environment environment) {
        if (!storage.configured() && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("S3_BUCKET is required in the prod profile");
        }
        return new StorageSettingsCheck();
    }

    /** Marker bean: only exists so the check above runs at start-up. */
    public static final class StorageSettingsCheck {
    }
}
