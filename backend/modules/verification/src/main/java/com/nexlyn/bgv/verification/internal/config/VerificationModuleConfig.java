package com.nexlyn.bgv.verification.internal.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Runs this module's own Flyway history against its own schema, independent of
 * every other module's migrations (see CLAUDE.md {@literal §4.2} rule 5: own schema, own migrations).
 */
@Configuration
public class VerificationModuleConfig {

    @Bean(initMethod = "migrate")
    public Flyway verificationFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("verification")
                .locations("classpath:db/migration/verification")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
    }
}
