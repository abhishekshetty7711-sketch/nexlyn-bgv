package com.nexlyn.bgv.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexlyn.bgv.CasesTestApplication;
import com.nexlyn.bgv.auth.internal.security.JwtService;
import com.nexlyn.bgv.auth.internal.service.ClientInfo;
import com.nexlyn.bgv.auth.internal.service.SessionService;
import com.nexlyn.bgv.common.security.Permission;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Shared set-up for the cases module's tests: one real PostgreSQL for the whole test run, the real
 * migrations, and helpers to act as different admins over real HTTP. Tokens are forged with the real
 * signing key (there is no need to walk through the 2FA login in every test).
 */
@SpringBootTest(classes = CasesTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "nexlyn.auth.bootstrap.email=owner@example.com",
        "nexlyn.auth.bootstrap.password=Tr1cky-Orange-Kettle",
        "nexlyn.auth.rate-limit.ip-requests=100000",
        "nexlyn.auth.rate-limit.email-attempts=100000",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.open-in-view=false"
})
public abstract class CasesIntegrationTestBase {

    protected static final String OWNER = "owner@example.com";

    /** One container for every test class in the run; the JVM exit (and Ryuk) removes it. */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JwtService jwt;
    @Autowired protected SessionService sessions;

    protected UUID ownerId;
    protected Instant testStart;

    @BeforeEach
    void cleanCases() {
        // Children first; audit rows cannot be deleted (append-only), tests filter them by time instead.
        // The status history is append-only (a trigger refuses DELETE), so tests empty it with TRUNCATE.
        jdbc.update("TRUNCATE cases.case_status_history");
        jdbc.update("DELETE FROM cases.check_free_sections");
        jdbc.update("DELETE FROM cases.check_details");
        jdbc.update("DELETE FROM cases.check_fields");
        jdbc.update("DELETE FROM cases.verification_checks");
        jdbc.update("DELETE FROM cases.case_assignments");
        jdbc.update("DELETE FROM cases.candidates");
        jdbc.update("DELETE FROM cases.cases");
        jdbc.update("DELETE FROM cases.report_id_sequences");
        jdbc.update("DELETE FROM cases.clients");
        jdbc.update("DELETE FROM auth.admins WHERE lower(email) <> ?", OWNER);
        jdbc.update("DELETE FROM auth.refresh_tokens");
        ownerId = jdbc.queryForObject("SELECT id FROM auth.admins WHERE lower(email) = ?", UUID.class, OWNER);
        testStart = Instant.now();
    }

    // ---- who is acting ------------------------------------------------------------------------

    protected static String[] all() {
        return Arrays.stream(Permission.values()).map(Enum::name).toArray(String[]::new);
    }

    /** A new admin row (no password login needed), for assignments and per-admin visibility tests. */
    protected UUID newAdmin(String email, String fullName) {
        return jdbc.queryForObject(
                "INSERT INTO auth.admins (email, full_name, password_hash) VALUES (?, ?, 'unused') RETURNING id",
                UUID.class, email, fullName);
    }

    protected String tokenFor(UUID adminId, String email, String... permissions) {
        UUID session = sessions.startSession(adminId, new ClientInfo("127.0.0.1", "JUnit")).familyId();
        return jwt.issueAccessToken(adminId, email, List.of("TEST"), List.of(permissions), session);
    }

    /** The owner with every permission. */
    protected String superToken() {
        return tokenFor(ownerId, OWNER, all());
    }

    /** A map that allows null values (Map.of does not), for request bodies. */
    protected static java.util.Map<String, Object> obj(Object... keyValues) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    // ---- HTTP helpers -----------------------------------------------------------------------------

    protected MvcResult send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request).andReturn();
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    protected String code(MvcResult result) throws Exception {
        return body(result).get("code").asText();
    }

    protected List<String> auditActionsSinceStart() {
        return jdbc.queryForList("SELECT action FROM auth.audit_log WHERE at >= ? ORDER BY at",
                String.class, Timestamp.from(testStart));
    }
}
