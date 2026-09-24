package com.nexlyn.bgv.auth.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexlyn.bgv.auth.AuthTestApplication;
import com.nexlyn.bgv.auth.CaseAssignmentLookup;
import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nexlyn.bgv.auth.internal.security.JwtKeyProvider;
import com.nexlyn.bgv.auth.internal.security.JwtService;
import com.nexlyn.bgv.auth.internal.service.ClientInfo;
import com.nexlyn.bgv.auth.internal.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Authorization over real HTTP (CLAUDE.md {@literal §11.4}, phase 2 "done when"): unauthenticated,
 * wrong permission, expired, tampered and revoked tokens, the per-case rule for analysts, CORS and
 * security headers.
 */
@Testcontainers
@SpringBootTest(classes = AuthTestApplication.class)
@AutoConfigureMockMvc
@Import(SecurityIntegrationTest.Assignments.class)
@TestPropertySource(properties = {
        "nexlyn.cors.allowed-origin=http://localhost:5173",
        "nexlyn.auth.bootstrap.email=owner@example.com",
        "nexlyn.auth.bootstrap.password=Tr1cky-Orange-Kettle",
        "nexlyn.auth.rate-limit.ip-requests=100000",
        "nexlyn.auth.rate-limit.email-attempts=100000",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.open-in-view=false"
})
class SecurityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Stands in for the cases module: which case is assigned to which admin. */
    @TestConfiguration
    static class Assignments {
        static final Set<String> ASSIGNED = ConcurrentHashMap.newKeySet();

        @Bean
        CaseAssignmentLookup fakeAssignments() {
            return (caseId, adminId) -> ASSIGNED.contains(caseId + ":" + adminId);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired JwtKeyProvider keys;
    @Autowired AuthProperties properties;
    @Autowired SessionService sessions;

    UUID adminId;
    UUID sessionId;

    @BeforeEach
    void newSession() {
        jdbc.update("DELETE FROM auth.refresh_tokens");
        Assignments.ASSIGNED.clear();
        adminId = jdbc.queryForObject("SELECT id FROM auth.admins", UUID.class);
        sessionId = sessions.startSession(adminId, new ClientInfo("127.0.0.1", "JUnit")).familyId();
    }

    // ---- helpers ------------------------------------------------------------------------

    private String token(String... permissions) {
        return jwt.issueAccessToken(adminId, "owner@example.com", List.of("TEST_ROLE"), List.of(permissions), sessionId);
    }

    private MvcResult call(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
    }

    private MvcResult getWith(String path, String token) throws Exception {
        return call(get(path).header("Authorization", "Bearer " + token));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    // ---- authentication ------------------------------------------------------------------------

    @Test
    void requestsWithoutATokenAreRefusedWith401AndAStandardBody() throws Exception {
        MvcResult result = call(get("/api/me"));
        assertThat(status(result)).isEqualTo(401);
        assertThat(body(result).get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(call(post("/api/test/anything")).getResponse().getStatus()).isEqualTo(401);
        assertThat(call(delete("/api/cases/" + UUID.randomUUID())).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anUnknownApiPathDoesNotRevealWhetherItExistsToAnAnonymousCaller() throws Exception {
        assertThat(status(call(get("/api/does-not-exist")))).isEqualTo(401);
        assertThat(status(getWith("/api/does-not-exist", token()))).isEqualTo(404);
    }

    @Test
    void nothingOutsideApiAndActuatorIsServed() throws Exception {
        assertThat(status(call(get("/")))).isEqualTo(401);
        assertThat(status(call(get("/swagger-ui.html")))).isEqualTo(401);
        assertThat(status(getWith("/internal", token("SETTINGS_MANAGE")))).isEqualTo(403);
    }

    @Test
    void badTokensAreRefused() throws Exception {
        String good = token("CASE_CREATE");
        String[] parts = good.split("\\.");
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(java.util.Base64.getUrlDecoder().decode(parts[1])).replace("CASE_CREATE", "USER_MANAGE").getBytes());

        JwtService inThePast = new JwtService(keys, properties, Clock.fixed(Instant.now().minus(Duration.ofHours(1)), java.time.ZoneOffset.UTC));
        String expired = inThePast.issueAccessToken(adminId, "owner@example.com", List.of(), List.of("CASE_CREATE"), sessionId);
        String challenge = jwt.issueChallenge(adminId, JwtService.ChallengePurpose.VERIFY);

        for (String bad : List.of("garbage", "a.b.c", parts[0] + "." + tamperedPayload + "." + parts[2], expired, challenge)) {
            MvcResult result = getWith("/api/me", bad);
            assertThat(status(result)).as("token %s...", bad.substring(0, Math.min(12, bad.length()))).isEqualTo(401);
        }
        // A non-Bearer scheme is ignored, not accepted.
        assertThat(status(call(get("/api/me").header("Authorization", "Basic " + good)))).isEqualTo(401);
        assertThat(status(getWith("/api/me", good))).as("the untouched token works").isEqualTo(200);
    }

    @Test
    void aTokenIsRefusedOnceItsSessionIsGone() throws Exception {
        String token = token("CASE_CREATE");
        assertThat(status(getWith("/api/me", token))).isEqualTo(200);

        sessions.endAllSessions(adminId); // e.g. logout, admin disabled, or theft detected
        assertThat(status(getWith("/api/me", token))).isEqualTo(401);
    }

    @Test
    void aTokenForASessionThatNeverExistedIsRefused() throws Exception {
        String orphan = jwt.issueAccessToken(adminId, "owner@example.com", List.of(), List.of("USER_MANAGE"), UUID.randomUUID());
        assertThat(status(getWith("/api/me", orphan))).isEqualTo(401);
    }

    @Test
    void theAuthEndpointsAreReachableWithoutAToken() throws Exception {
        MockHttpServletResponse login = call(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"nobody@example.com\",\"password\":\"Wrong-Password-1!\"}")).getResponse();
        assertThat(login.getStatus()).isEqualTo(401);
        assertThat(login.getContentAsString()).contains("INVALID_CREDENTIALS").doesNotContain("UNAUTHENTICATED");
        assertThat(call(post("/api/auth/logout")).getResponse().getStatus()).as("reaches the CSRF check").isEqualTo(403);
    }

    @Test
    void meReturnsTheSignedInAdminAndHisPermissions() throws Exception {
        MvcResult result = getWith("/api/me", token("CASE_CREATE", "AUDIT_READ"));
        assertThat(status(result)).isEqualTo(200);
        JsonNode me = body(result);
        assertThat(me.get("id").asText()).isEqualTo(adminId.toString());
        assertThat(me.get("email").asText()).isEqualTo("owner@example.com");
        assertThat(me.get("fullName").asText()).isEqualTo("Super Admin");
        assertThat(me.get("roles")).hasSize(1);
        assertThat(me.get("permissions")).extracting(JsonNode::asText).containsExactly("AUDIT_READ", "CASE_CREATE");
        assertThat(me.has("passwordHash")).isFalse();
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
    }

    // ---- authorization -------------------------------------------------------------------------

    @Test
    void aMissingPermissionGives403WithoutSayingWhichOne() throws Exception {
        MvcResult denied = getWith("/api/test/needs-user-manage", token("CASE_CREATE"));
        assertThat(status(denied)).isEqualTo(403);
        assertThat(body(denied).get("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(denied.getResponse().getContentAsString()).doesNotContain("USER_MANAGE");

        assertThat(status(getWith("/api/test/needs-user-manage", token("USER_MANAGE")))).isEqualTo(200);
    }

    @Test
    void anAnalystReachesOnlyAssignedCases() throws Exception {
        UUID mine = UUID.randomUUID();
        UUID someoneElses = UUID.randomUUID();
        Assignments.ASSIGNED.add(mine + ":" + adminId);
        String analyst = token("CASE_READ_ASSIGNED", "CASE_UPDATE");

        assertThat(status(getWith("/api/test/cases/" + mine, analyst))).isEqualTo(200);
        assertThat(status(getWith("/api/test/cases/" + mine + "/update", analyst))).isEqualTo(200);
        assertThat(status(getWith("/api/test/cases/" + someoneElses, analyst))).as("unassigned case").isEqualTo(403);
        assertThat(status(getWith("/api/test/cases/" + UUID.randomUUID(), analyst))).as("guessed id").isEqualTo(403);
        assertThat(status(getWith("/api/test/cases/" + mine + "/delete", analyst))).as("assigned but no CASE_DELETE").isEqualTo(403);
    }

    @Test
    void anAdminWithReadAllReachesEveryCaseButStillNeedsThePermissionForTheAction() throws Exception {
        UUID any = UUID.randomUUID();
        String ops = token("CASE_READ_ALL", "CASE_UPDATE");
        assertThat(status(getWith("/api/test/cases/" + any, ops))).isEqualTo(200);
        assertThat(status(getWith("/api/test/cases/" + any + "/update", ops))).isEqualTo(200);

        String auditor = token("CASE_READ_ALL");
        assertThat(status(getWith("/api/test/cases/" + any, auditor))).as("auditor can read").isEqualTo(200);
        assertThat(status(getWith("/api/test/cases/" + any + "/update", auditor))).as("auditor cannot write").isEqualTo(403);
    }

    @Test
    void withoutAnyCasePermissionEvenAnAssignedCaseIsRefused() throws Exception {
        UUID mine = UUID.randomUUID();
        Assignments.ASSIGNED.add(mine + ":" + adminId);
        assertThat(status(getWith("/api/test/cases/" + mine, token("AUDIT_READ")))).isEqualTo(403);
    }

    // ---- CORS and headers ---------------------------------------------------------------------------

    @Test
    void onlyTheConfiguredFrontendOriginPassesCorsWithCredentials() throws Exception {
        MockHttpServletResponse ok = call(options("/api/me")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,x-csrf-token")).getResponse();
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(ok.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(ok.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");

        MockHttpServletResponse evil = call(options("/api/me")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET")).getResponse();
        assertThat(evil.getStatus()).isEqualTo(403);
        assertThat(evil.getHeader("Access-Control-Allow-Origin")).isNull();

        MockHttpServletResponse actual = call(get("/api/me").header("Origin", "https://evil.example")).getResponse();
        assertThat(actual.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void responsesCarryTheSecurityHeaders() throws Exception {
        MockHttpServletResponse response = call(get("/api/me").secure(true)).getResponse();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'none'", "frame-ancestors 'none'");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Permissions-Policy")).contains("camera=()");
        assertThat(response.getHeader("Strict-Transport-Security")).contains("max-age=31536000", "includeSubDomains");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }
}
