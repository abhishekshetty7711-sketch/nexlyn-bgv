package com.nexlyn.bgv.reports.internal.config;

import com.nexlyn.bgv.reports.internal.render.ImageEmbedder;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Runs this module's own Flyway history against its own schema, independent of
 * every other module's migrations (see CLAUDE.md {@literal §4.2} rule 5: own schema, own migrations),
 * and reads the report settings.
 */
@Configuration
@EnableConfigurationProperties(ReportsProperties.class)
public class ReportsModuleConfig {

    @Bean(initMethod = "migrate")
    public Flyway reportsFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("reports")
                .locations("classpath:db/migration/reports")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean
    public ImageEmbedder imageEmbedder(ReportsProperties properties) {
        return new ImageEmbedder(properties.imageSide());
    }
}
