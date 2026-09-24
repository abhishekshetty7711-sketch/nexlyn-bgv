package com.nexlyn.bgv;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Boots the complete application (every module) against a real PostgreSQL that is initialised with
 * the same schema script Docker Compose uses. Catches wiring problems the per-module tests cannot.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "nexlyn.auth.bootstrap.email=owner@example.com",
        "nexlyn.auth.bootstrap.password=Tr1cky-Orange-Kettle",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.open-in-view=false"
})
class BgvApplicationSmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../../infra/local/postgres/init/01-create-schemas.sql"),
                    "/docker-entrypoint-initdb.d/01-create-schemas.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void healthEndpointIsUp() throws Exception {
        MvcResult health = mvc.perform(get("/actuator/health")).andReturn();
        assertThat(health.getResponse().getStatus()).isEqualTo(200);
        assertThat(health.getResponse().getContentAsString()).contains("\"status\":\"UP\"");
    }

    @Test
    void everyResponseCarriesACorrelationIdThatErrorBodiesRepeat() throws Exception {
        MvcResult unauthenticated = mvc.perform(get("/api/me")).andReturn();
        String header = unauthenticated.getResponse().getHeader("X-Correlation-Id");
        assertThat(header).isNotBlank();
        assertThat(json.readTree(unauthenticated.getResponse().getContentAsString()).get("correlationId").asText()).isEqualTo(header);

        MvcResult echoed = mvc.perform(get("/api/me").header("X-Correlation-Id", "trace-2026-09-25-abcdef")).andReturn();
        assertThat(echoed.getResponse().getHeader("X-Correlation-Id")).isEqualTo("trace-2026-09-25-abcdef");
        MvcResult replaced = mvc.perform(get("/api/me").header("X-Correlation-Id", "bad id!")).andReturn();
        assertThat(replaced.getResponse().getHeader("X-Correlation-Id")).isNotEqualTo("bad id!").matches("[0-9a-f-]{36}");
        assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getHeader("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void theProductionJsonLogFormatIsOnTheClasspath() throws Exception {
        assertThat(Class.forName("net.logstash.logback.encoder.LogstashEncoder")).isNotNull();
        assertThat(getClass().getResource("/logback-spring.xml")).isNotNull();
    }

    @Test
    void theApiIsClosedByDefaultAndOnlyHealthIsPublic() throws Exception {
        MvcResult me = mvc.perform(get("/api/me")).andReturn();
        assertThat(me.getResponse().getStatus()).isEqualTo(401);
        assertThat(me.getResponse().getContentAsString()).contains("UNAUTHENTICATED");
        assertThat(mvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getHeader("X-Content-Type-Options"))
                .isEqualTo("nosniff");
    }

    @Test
    void everyModuleHasItsOwnMigrationHistory() {
        List<String> schemas = jdbc.queryForList(
                "SELECT table_schema FROM information_schema.tables WHERE table_name = 'flyway_schema_history'", String.class);
        assertThat(schemas).containsExactlyInAnyOrder("auth", "cases", "verification", "documents", "reports");
    }

    @Test
    void theBootstrapAdminCanStartLoggingInThroughTheFullApplication() throws Exception {
        MvcResult ok = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "Owner@Example.com", "password", "Tr1cky-Orange-Kettle"))))
                .andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(ok.getResponse().getContentAsString()).get("status").asText()).isEqualTo("2FA_SETUP_REQUIRED");

        MvcResult bad = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "owner@example.com", "password", "Wrong-Password-1!"))))
                .andReturn();
        assertThat(bad.getResponse().getStatus()).isEqualTo(401);
        assertThat(json.readTree(bad.getResponse().getContentAsString()).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }
}
