package com.nexlyn.bgv.documents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexlyn.bgv.DocumentsTestApplication;
import com.nexlyn.bgv.auth.internal.security.JwtService;
import com.nexlyn.bgv.auth.internal.service.ClientInfo;
import com.nexlyn.bgv.auth.internal.service.SessionService;
import com.nexlyn.bgv.common.security.Permission;
import com.nexlyn.bgv.documents.internal.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared set-up for the documents module's tests: a real PostgreSQL, the real migrations, real HTTP
 * through MockMvc, and an in-memory file store (the S3 adapter has its own test against real S3 servers).
 * Cases are made through the cases module's own API, exactly as the frontend does.
 */
@SpringBootTest(classes = DocumentsTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "nexlyn.auth.bootstrap.email=owner@example.com",
        "nexlyn.auth.bootstrap.password=Tr1cky-Orange-Kettle",
        "nexlyn.auth.rate-limit.ip-requests=100000",
        "nexlyn.auth.rate-limit.email-attempts=100000",
        "nexlyn.documents.max-bytes=200000",
        "nexlyn.documents.max-pixels=1000000",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.open-in-view=false"
})
@org.springframework.context.annotation.Import(DocumentsIntegrationTestBase.StorageConfig.class)
public abstract class DocumentsIntegrationTestBase {

    protected static final String OWNER = "owner@example.com";

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

    /** Files kept in memory, keyed like the real store, so tests can look at what was stored. */
    public static class InMemoryStorage implements StorageService {
        public final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public void put(String key, byte[] content, String contentType) {
            objects.put(key, content.clone());
        }

        @Override
        public byte[] get(String key) {
            byte[] found = objects.get(key);
            if (found == null) {
                throw new com.nexlyn.bgv.common.error.ApiException(com.nexlyn.bgv.common.error.ErrorCode.NOT_FOUND, "The file is missing from storage.");
            }
            return found.clone();
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    @TestConfiguration
    public static class StorageConfig {
        @Bean
        @Primary
        InMemoryStorage inMemoryStorage() {
            return new InMemoryStorage();
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JwtService jwt;
    @Autowired protected SessionService sessions;
    @Autowired protected InMemoryStorage storage;

    protected UUID ownerId;
    protected Instant testStart;

    @BeforeEach
    void cleanUp() {
        jdbc.update("DELETE FROM documents.documents");
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
        storage.objects.clear();
        ownerId = jdbc.queryForObject("SELECT id FROM auth.admins WHERE lower(email) = ?", UUID.class, OWNER);
        testStart = Instant.now();
    }

    // ---- who is acting ------------------------------------------------------------------------

    protected static String[] all() {
        return Arrays.stream(Permission.values()).map(Enum::name).toArray(String[]::new);
    }

    protected UUID newAdmin(String email, String fullName) {
        return jdbc.queryForObject(
                "INSERT INTO auth.admins (email, full_name, password_hash) VALUES (?, ?, 'unused') RETURNING id",
                UUID.class, email, fullName);
    }

    protected String tokenFor(UUID adminId, String email, String... permissions) {
        UUID session = sessions.startSession(adminId, new ClientInfo("127.0.0.1", "JUnit")).familyId();
        return jwt.issueAccessToken(adminId, email, List.of("TEST"), List.of(permissions), session);
    }

    protected String superToken() {
        return tokenFor(ownerId, OWNER, all());
    }

    protected static Map<String, Object> obj(Object... keyValues) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
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

    /** Uploads a file as multipart under the field name "file". */
    protected MvcResult upload(String url, String token, String filename, byte[] content, String... params) throws Exception {
        var request = multipart(url).file(new MockMultipartFile("file", filename, "application/octet-stream", content));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
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

    // ---- a case with a check, made the way the frontend makes them ----------------------------------

    protected UUID newClient() throws Exception {
        MvcResult client = send(post("/api/clients"), superToken(), obj("name", "Acme Corp", "displayName", "Acme Corp"));
        assertThat(status(client)).isEqualTo(201);
        return UUID.fromString(body(client).get("id").asText());
    }

    /** A case created by this admin (so they are its preparer and can reach it). */
    protected JsonNode newCase(UUID clientId, String token) throws Exception {
        MvcResult created = send(post("/api/cases"), token, obj("clientId", clientId.toString()));
        assertThat(status(created)).as(created.getResponse().getContentAsString()).isEqualTo(201);
        return body(created);
    }

    protected JsonNode newCheck(String caseId, String type, String token) throws Exception {
        MvcResult added = send(post("/api/cases/" + caseId + "/checks"), token, obj("type", type));
        assertThat(status(added)).as(added.getResponse().getContentAsString()).isEqualTo(201);
        return body(added);
    }
}
