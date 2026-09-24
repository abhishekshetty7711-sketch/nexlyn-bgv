package com.nexlyn.bgv.auth.internal.config;

import com.nexlyn.bgv.auth.internal.service.BootstrapAdminService;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Runs this module's own Flyway history against its own schema, independent of
 * every other module's migrations (see CLAUDE.md {@literal §4.2} rule 5: own schema, own migrations).
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthModuleConfig {

    @Bean(initMethod = "migrate")
    public Flyway authFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("auth")
                .locations("classpath:db/migration/auth")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
    }

    /** One clock for the whole app so time-dependent rules (lockout, expiry) are testable. */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Runs after startup, once every Flyway migration has been applied. */
    @Bean
    public ApplicationRunner bootstrapAdminRunner(BootstrapAdminService bootstrap) {
        return args -> bootstrap.bootstrapIfNeeded();
    }
}
