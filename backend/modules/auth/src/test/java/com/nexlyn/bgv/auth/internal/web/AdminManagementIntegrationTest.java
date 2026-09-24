package com.nexlyn.bgv.auth.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexlyn.bgv.auth.AuthTestApplication;
import com.nexlyn.bgv.auth.internal.security.JwtService;
import com.nexlyn.bgv.auth.internal.service.ClientInfo;
import com.nexlyn.bgv.auth.internal.service.PasswordHasher;
import com.nexlyn.bgv.auth.internal.service.SessionService;
import com.nexlyn.bgv.auth.internal.service.TotpService;
import com.nexlyn.bgv.common.security.Permission;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Admin, role, invitation, password and audit-log management over real HTTP against a real
 * PostgreSQL (CLAUDE.md {@literal §9.1}, {@literal §11}).
 */
@Testcontainers
@SpringBootTest(classes = AuthTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "nexlyn.auth.bootstrap.email=owner@example.com",
        "nexlyn.auth.bootstrap.password=Tr1cky-Orange-Kettle",
        "nexlyn.auth.rate-limit.ip-requests=100000",
        "nexlyn.auth.rate-limit.email-attempts=100000",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.open-in-view=false"
})
class AdminManagementIntegrationTest {

    static final String OWNER = "owner@example.com";
    static final String PASSWORD = "Tr1cky-Orange-Kettle";
    static final String STRONG = "Blue-Whale-Sings-42";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired SessionService sessions;
    @Autowired PasswordHasher hasher;
    @Autowired TotpService totp;

    UUID ownerId;
    Instant testStart;

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM auth.invitations");
        jdbc.update("DELETE FROM auth.admins WHERE lower(email) <> ?", OWNER); // cascades roles, tokens, 2FA
        jdbc.update("DELETE FROM auth.admin_roles");
        jdbc.update("DELETE FROM auth.roles WHERE NOT system_role");
        jdbc.update("DELETE FROM auth.refresh_tokens");
        jdbc.update("UPDATE auth.admins SET password_hash = ?, mfa_enabled = false, failed_attempts = 0,"
                + " lockout_count = 0, locked_until = NULL, status = 'ACTIVE'", hasher.hash(PASSWORD));
        jdbc.update("INSERT INTO auth.admin_roles (admin_id, role_id) SELECT a.id, r.id FROM auth.admins a,"
                + " auth.roles r WHERE r.code = 'SUPER_ADMIN'");
        ownerId = jdbc.queryForObject("SELECT id FROM auth.admins", UUID.class);
        testStart = Instant.now();
    }

    // ---- helpers ------------------------------------------------------------------------

    private String tokenFor(UUID adminId, String email, String... permissions) {
        UUID session = sessions.startSession(adminId, new ClientInfo("127.0.0.1", "JUnit")).familyId();
        return jwt.issueAccessToken(adminId, email, List.of("TEST"), List.of(permissions), session);
    }

    /** The owner with every permission. */
    private String superToken() {
        return tokenFor(ownerId, OWNER, Arrays.stream(Permission.values()).map(Enum::name).toArray(String[]::new));
    }

    private MvcResult send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private String code(MvcResult result) throws Exception {
        return body(result).get("code").asText();
    }

    private UUID roleId(String code) {
        return jdbc.queryForObject("SELECT id FROM auth.roles WHERE code = ?", UUID.class, code);
    }

    private UUID adminId(String email) {
        return jdbc.queryForObject("SELECT id FROM auth.admins WHERE lower(email) = ?", UUID.class, email);
    }

    private String invite(String token, String email, UUID... roleIds) throws Exception {
        MvcResult result = send(post("/api/admins/invitations"), token, Map.of("email", email, "roleIds", List.of(roleIds)));
        assertThat(status(result)).as("invite %s: %s", email, result.getResponse().getContentAsString()).isEqualTo(201);
        return body(result).get("inviteToken").asText();
    }

    private MvcResult accept(String inviteToken, String password) throws Exception {
        return send(post("/api/auth/invitations/accept"), null,
                Map.of("inviteToken", inviteToken, "fullName", "New Person", "password", password));
    }

    /** Invite and accept in one go; returns the new admin's id (2FA not yet set up). */
    private UUID createAdmin(String email, String roleCode) throws Exception {
        String token = invite(superToken(), email, roleId(roleCode));
        assertThat(status(accept(token, STRONG))).isEqualTo(200);
        return adminId(email);
    }

    private List<String> auditActionsSinceStart() {
        return jdbc.queryForList("SELECT action FROM auth.audit_log WHERE at >= ? ORDER BY at",
                String.class, Timestamp.from(testStart));
    }

    // ---- invitations ------------------------------------------------------------------------

    @Test
    void anInvitedPersonSetsAPasswordThenEnrolsTwoFactorAndIsSignedInWithTheInvitedRole() throws Exception {
        String token = invite(superToken(), "New.Admin@Example.com", roleId("OPS_MANAGER"));

        // Only a hash of the link token is stored.
        assertThat(jdbc.queryForList("SELECT token_hash FROM auth.invitations", String.class))
                .hasSize(1).allMatch(h -> h.matches("[0-9a-f]{64}") && !h.equals(token));

        MvcResult accepted = accept(token, STRONG);
        assertThat(status(accepted)).isEqualTo(200);
        JsonNode challenge = body(accepted);
        assertThat(challenge.get("status").asText()).isEqualTo("2FA_SETUP_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT mfa_enabled FROM auth.admins WHERE email = 'new.admin@example.com'", Boolean.class))
                .isFalse();

        // Continue exactly like a first login: 2FA setup, then confirm with the first code.
        String setupToken = challenge.get("challengeToken").asText();
        String secret = body(send(post("/api/auth/2fa/setup"), null, Map.of("challengeToken", setupToken))).get("secret").asText();
        MvcResult confirmed = send(post("/api/auth/2fa/confirm"), null, Map.of("challengeToken", setupToken,
                "code", totp.codeForStep(secret, totp.stepAt(Instant.now()))));
        assertThat(status(confirmed)).isEqualTo(200);

        MvcResult me = send(get("/api/me"), body(confirmed).get("accessToken").asText(), null);
        assertThat(status(me)).isEqualTo(200);
        assertThat(body(me).get("email").asText()).isEqualTo("new.admin@example.com");
        assertThat(body(me).get("roles")).extracting(JsonNode::asText).containsExactly("OPS_MANAGER");
        assertThat(body(me).get("permissions")).hasSize(16);

        assertThat(auditActionsSinceStart()).contains("ADMIN_INVITED", "INVITATION_ACCEPTED", "TWO_FACTOR_ENABLED", "LOGIN_SUCCESS");
    }

    @Test
    void aLinkWorksOnceAndEveryProblemWithALinkLooksTheSame() throws Exception {
        String token = invite(superToken(), "one@example.com", roleId("ANALYST"));
        assertThat(status(accept(token, STRONG))).isEqualTo(200);

        MvcResult reuse = accept(token, STRONG);
        assertThat(status(reuse)).isEqualTo(400);
        assertThat(code(reuse)).isEqualTo("INVALID_INVITATION");

        MvcResult unknown = accept("not-a-real-token", STRONG);
        assertThat(status(unknown)).isEqualTo(400);
        assertThat(unknown.getResponse().getContentAsString()).isEqualTo(reuse.getResponse().getContentAsString());

        String expiring = invite(superToken(), "two@example.com", roleId("ANALYST"));
        jdbc.update("UPDATE auth.invitations SET expires_at = now() - interval '1 second' WHERE lower(email) = 'two@example.com'");
        MvcResult expired = accept(expiring, STRONG);
        assertThat(status(expired)).isEqualTo(400);
        assertThat(expired.getResponse().getContentAsString()).isEqualTo(reuse.getResponse().getContentAsString());
    }

    @Test
    void aWeakPasswordIsRefusedWithoutBurningTheLink() throws Exception {
        String token = invite(superToken(), "weak@example.com", roleId("ANALYST"));
        MvcResult weak = accept(token, "password");
        assertThat(status(weak)).isEqualTo(400);
        assertThat(code(weak)).isEqualTo("WEAK_PASSWORD");
        assertThat(weak.getResponse().getContentAsString()).doesNotContain("\"password\":\"password\"");

        assertThat(status(accept(token, STRONG))).as("the same link still works").isEqualTo(200);
    }

    @Test
    void aNewInvitationReplacesAnEarlierOneAndRevokedOnesStopWorking() throws Exception {
        String first = invite(superToken(), "again@example.com", roleId("ANALYST"));
        String second = invite(superToken(), "again@example.com", roleId("ANALYST"));
        assertThat(status(accept(first, STRONG))).as("older link replaced").isEqualTo(400);

        MvcResult pending = send(get("/api/admins/invitations"), superToken(), null);
        assertThat(body(pending)).hasSize(1);
        assertThat(body(pending).get(0).get("email").asText()).isEqualTo("again@example.com");
        assertThat(body(pending).toString()).doesNotContain(second);

        UUID invitationId = UUID.fromString(body(pending).get(0).get("id").asText());
        assertThat(status(send(delete("/api/admins/invitations/" + invitationId), superToken(), null))).isEqualTo(204);
        assertThat(status(accept(second, STRONG))).as("revoked link").isEqualTo(400);
        assertThat(body(send(get("/api/admins/invitations"), superToken(), null))).isEmpty();
    }

    @Test
    void invitationInputIsValidated() throws Exception {
        String su = superToken();
        assertThat(status(send(post("/api/admins/invitations"), su, Map.of("email", OWNER, "roleIds", List.of(roleId("ANALYST")))))).as("already an admin").isEqualTo(409);
        assertThat(status(send(post("/api/admins/invitations"), su, Map.of("email", "x@example.com", "roleIds", List.of(UUID.randomUUID()))))).as("unknown role").isEqualTo(400);
        assertThat(status(send(post("/api/admins/invitations"), su, Map.of("email", "x@example.com", "roleIds", List.of())))).as("no roles").isEqualTo(400);
        assertThat(status(send(post("/api/admins/invitations"), su, Map.of("email", "not-an-email", "roleIds", List.of(roleId("ANALYST")))))).as("bad email").isEqualTo(400);
    }

    // ---- who may do what ---------------------------------------------------------------------------

    @Test
    void everyManagementEndpointNeedsItsOwnPermission() throws Exception {
        String nobody = tokenFor(ownerId, OWNER, "CASE_CREATE");
        UUID some = UUID.randomUUID();
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/admins"), get("/api/admins/" + some), post("/api/admins/" + some + "/disable"),
                post("/api/admins/" + some + "/revoke-sessions"), get("/api/admins/invitations"),
                get("/api/roles"), get("/api/roles/" + some), get("/api/permissions"), delete("/api/roles/" + some),
                get("/api/audit-log"))) {
            MvcResult result = send(request, nobody, null);
            assertThat(status(result)).as(result.getRequest().getRequestURI()).isEqualTo(403);
            assertThat(code(result)).isEqualTo("FORBIDDEN");
        }
        for (String path : List.of("/api/admins", "/api/roles", "/api/audit-log", "/api/permissions")) {
            assertThat(status(send(get(path), null, null))).as(path).isEqualTo(401);
        }
        // Holding only one of the management permissions is not enough for the others.
        String userManager = tokenFor(ownerId, OWNER, "USER_MANAGE");
        assertThat(status(send(get("/api/admins"), userManager, null))).isEqualTo(200);
        assertThat(status(send(get("/api/roles"), userManager, null))).as("role list is readable to assign roles").isEqualTo(200);
        assertThat(status(send(get("/api/permissions"), userManager, null))).isEqualTo(403);
        assertThat(status(send(post("/api/roles"), userManager, Map.of("code", "NEW_ROLE", "name", "N", "permissions", List.of())))).isEqualTo(403);
        assertThat(status(send(get("/api/audit-log"), userManager, null))).isEqualTo(403);
    }

    @Test
    void nobodyCanGrantMoreAccessThanTheyHold() throws Exception {
        MvcResult clerk = send(post("/api/roles"), superToken(),
                Map.of("code", "CASE_CLERK", "name", "Case clerk", "permissions", List.of("CASE_CREATE")));
        assertThat(status(clerk)).isEqualTo(201);
        UUID clerkRole = UUID.fromString(body(clerk).get("id").asText());

        String limited = tokenFor(ownerId, OWNER, "USER_MANAGE", "CASE_CREATE");
        assertThat(status(send(post("/api/admins/invitations"), limited,
                Map.of("email", "a@example.com", "roleIds", List.of(roleId("SUPER_ADMIN")))))).as("SUPER_ADMIN").isEqualTo(403);
        assertThat(status(send(post("/api/admins/invitations"), limited,
                Map.of("email", "b@example.com", "roleIds", List.of(roleId("OPS_MANAGER")))))).as("OPS_MANAGER").isEqualTo(403);
        assertThat(status(send(post("/api/admins/invitations"), limited,
                Map.of("email", "c@example.com", "roleIds", List.of(clerkRole))))).as("a role within their own access").isEqualTo(201);

        String roleManager = tokenFor(ownerId, OWNER, "ROLE_MANAGE", "CASE_CREATE");
        assertThat(status(send(post("/api/roles"), roleManager,
                Map.of("code", "GREEDY", "name", "Greedy", "permissions", List.of("CASE_CREATE", "USER_MANAGE"))))).isEqualTo(403);
    }

    @Test
    void anAdminCannotManageSomeoneWhoHasMoreAccessThanThem() throws Exception {
        UUID ops = createAdmin("ops@example.com", "OPS_MANAGER");
        String weak = tokenFor(ownerId, OWNER, "USER_MANAGE", "CASE_CREATE");
        assertThat(status(send(post("/api/admins/" + ops + "/disable"), weak, null))).isEqualTo(403);
        assertThat(status(send(post("/api/admins/" + ops + "/revoke-sessions"), weak, null))).isEqualTo(403);
        assertThat(status(send(put("/api/admins/" + ops), weak, Map.of("fullName", "Hacked")))).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT status FROM auth.admins WHERE id = ?", String.class, ops)).isEqualTo("ACTIVE");
    }

    // ---- managing admins --------------------------------------------------------------------------

    @Test
    void changingRolesEndsTheirSessionsAndIsAudited() throws Exception {
        UUID ops = createAdmin("ops@example.com", "OPS_MANAGER");
        String opsToken = tokenFor(ops, "ops@example.com", "CASE_CREATE");
        assertThat(status(send(get("/api/me"), opsToken, null))).isEqualTo(200);

        MvcResult updated = send(put("/api/admins/" + ops), superToken(),
                Map.of("fullName", "Renamed Ops", "roleIds", List.of(roleId("QC_REVIEWER"))));
        assertThat(status(updated)).isEqualTo(200);
        assertThat(body(updated).get("fullName").asText()).isEqualTo("Renamed Ops");
        assertThat(body(updated).get("roles")).extracting(JsonNode::asText).containsExactly("QC_REVIEWER");
        assertThat(status(send(get("/api/me"), opsToken, null))).as("old token dies with the role change").isEqualTo(401);

        MvcResult audit = send(get("/api/audit-log?action=ADMIN_UPDATED&entityId=" + ops), tokenFor(ownerId, OWNER, "AUDIT_READ"), null);
        JsonNode entry = body(audit).get("items").get(0);
        assertThat(entry.get("before").get("roles")).extracting(JsonNode::asText).containsExactly("OPS_MANAGER");
        assertThat(entry.get("after").get("roles")).extracting(JsonNode::asText).containsExactly("QC_REVIEWER");
        assertThat(entry.get("actorEmail").asText()).isEqualTo(OWNER);
        assertThat(entry.toString()).doesNotContain("password").doesNotContain("argon2");
    }

    @Test
    void disablingAnAdminStopsLoginAndSessionsUntilTheyAreEnabledAgain() throws Exception {
        UUID ops = createAdmin("ops@example.com", "OPS_MANAGER");
        String opsToken = tokenFor(ops, "ops@example.com", "CASE_CREATE");

        assertThat(status(send(post("/api/admins/" + ops + "/disable"), superToken(), null))).isEqualTo(200);
        assertThat(status(send(get("/api/me"), opsToken, null))).as("session ended").isEqualTo(401);
        MvcResult login = send(post("/api/auth/login"), null, Map.of("email", "ops@example.com", "password", STRONG));
        assertThat(status(login)).isEqualTo(401);
        assertThat(code(login)).as("indistinguishable from a wrong password").isEqualTo("INVALID_CREDENTIALS");

        assertThat(status(send(post("/api/admins/" + ops + "/enable"), superToken(), null))).isEqualTo(200);
        assertThat(status(send(post("/api/auth/login"), null, Map.of("email", "ops@example.com", "password", STRONG)))).isEqualTo(200);
        assertThat(auditActionsSinceStart()).contains("ADMIN_DISABLED", "ADMIN_ENABLED");
    }

    @Test
    void anAdminCannotDisableThemselves() throws Exception {
        MvcResult result = send(post("/api/admins/" + ownerId + "/disable"), superToken(), null);
        assertThat(status(result)).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT status FROM auth.admins WHERE id = ?", String.class, ownerId)).isEqualTo("ACTIVE");
    }

    @Test
    void theSystemCanNeverBeLeftWithoutAnAdministratorAndTheChangeIsRolledBack() throws Exception {
        createAdmin("ops@example.com", "OPS_MANAGER");
        // The owner is the only one who can manage admins. Demoting themselves would lock everyone out.
        MvcResult result = send(put("/api/admins/" + ownerId), superToken(), Map.of("roleIds", List.of(roleId("OPS_MANAGER"))));
        assertThat(status(result)).isEqualTo(409);
        assertThat(jdbc.queryForList("SELECT r.code FROM auth.admin_roles ar JOIN auth.roles r ON r.id = ar.role_id"
                + " WHERE ar.admin_id = ?", String.class, ownerId)).containsExactly("SUPER_ADMIN");
    }

    @Test
    void unlockingAndRevokingSessionsWork() throws Exception {
        UUID ops = createAdmin("ops@example.com", "OPS_MANAGER");
        jdbc.update("UPDATE auth.admins SET failed_attempts = 3, lockout_count = 2, locked_until = now() + interval '1 hour' WHERE id = ?", ops);
        MvcResult unlocked = send(post("/api/admins/" + ops + "/unlock"), superToken(), null);
        assertThat(status(unlocked)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT locked_until IS NULL AND failed_attempts = 0 AND lockout_count = 0 FROM auth.admins WHERE id = ?",
                Boolean.class, ops)).isTrue();

        String opsToken = tokenFor(ops, "ops@example.com", "CASE_CREATE");
        assertThat(status(send(post("/api/admins/" + ops + "/revoke-sessions"), superToken(), null))).isEqualTo(204);
        assertThat(status(send(get("/api/me"), opsToken, null))).isEqualTo(401);
        assertThat(auditActionsSinceStart()).contains("ADMIN_UNLOCKED", "SESSIONS_REVOKED");
    }

    @Test
    void theAdminListIsPagedAndNeverContainsSecrets() throws Exception {
        createAdmin("a@example.com", "ANALYST");
        createAdmin("b@example.com", "ANALYST");
        MvcResult page = send(get("/api/admins?page=1&size=2"), superToken(), null);
        JsonNode result = body(page);
        assertThat(result.get("total").asInt()).isEqualTo(3);
        assertThat(result.get("page").asInt()).isEqualTo(1);
        assertThat(result.get("size").asInt()).isEqualTo(2);
        assertThat(result.get("items")).hasSize(1);
        assertThat(page.getResponse().getContentAsString()).doesNotContain("password").doesNotContain("argon2");
        assertThat(status(send(get("/api/admins/" + UUID.randomUUID()), superToken(), null))).isEqualTo(404);
        assertThat(status(send(get("/api/admins/not-a-uuid"), superToken(), null))).isEqualTo(400);
    }

    // ---- roles -------------------------------------------------------------------------------------

    @Test
    void permissionsAndBuiltInRolesAreListed() throws Exception {
        assertThat(body(send(get("/api/permissions"), superToken(), null))).hasSize(21);
        JsonNode roles = body(send(get("/api/roles"), superToken(), null));
        assertThat(roles).hasSize(5);
        JsonNode superRole = null;
        for (JsonNode r : roles) {
            if (r.get("code").asText().equals("SUPER_ADMIN")) {
                superRole = r;
            }
        }
        assertThat(superRole).isNotNull();
        assertThat(superRole.get("systemRole").asBoolean()).isTrue();
        assertThat(superRole.get("permissions")).hasSize(21);
        assertThat(superRole.get("memberCount").asInt()).isEqualTo(1);
    }

    @Test
    void customRolesCanBeCreatedChangedAndDeletedWithinTheRules() throws Exception {
        String su = superToken();
        assertThat(status(send(post("/api/roles"), su, Map.of("code", "bad code", "name", "X", "permissions", List.of())))).isEqualTo(400);
        assertThat(status(send(post("/api/roles"), su, Map.of("code", "OPS_MANAGER", "name", "X", "permissions", List.of())))).as("duplicate").isEqualTo(409);
        assertThat(status(send(post("/api/roles"), su, Map.of("code", "GHOST", "name", "X", "permissions", List.of("NOT_A_PERMISSION"))))).isEqualTo(400);

        MvcResult created = send(post("/api/roles"), su, Map.of("code", "CASE_VIEWER", "name", "Case viewer",
                "description", "Reads all cases", "permissions", List.of("CASE_READ_ALL")));
        assertThat(status(created)).isEqualTo(201);
        UUID id = UUID.fromString(body(created).get("id").asText());
        assertThat(body(created).get("systemRole").asBoolean()).isFalse();

        // Changing the permissions of a role ends the sessions of everyone who holds it.
        UUID holder = createAdmin("viewer@example.com", "ANALYST");
        assertThat(status(send(put("/api/admins/" + holder), su, Map.of("roleIds", List.of(id))))).isEqualTo(200);
        String holderToken = tokenFor(holder, "viewer@example.com", "CASE_READ_ALL");
        assertThat(status(send(get("/api/me"), holderToken, null))).isEqualTo(200);

        MvcResult changed = send(put("/api/roles/" + id), su, Map.of("permissions", List.of("CASE_READ_ALL", "AUDIT_READ")));
        assertThat(status(changed)).isEqualTo(200);
        assertThat(body(changed).get("permissions")).extracting(JsonNode::asText).containsExactly("AUDIT_READ", "CASE_READ_ALL");
        assertThat(status(send(get("/api/me"), holderToken, null))).as("holder's old token").isEqualTo(401);

        assertThat(status(send(delete("/api/roles/" + id), su, null))).as("still assigned").isEqualTo(409);
        assertThat(status(send(put("/api/admins/" + holder), su, Map.of("roleIds", List.of(roleId("ANALYST")))))).isEqualTo(200);
        assertThat(status(send(delete("/api/roles/" + id), su, null))).isEqualTo(204);
        assertThat(status(send(get("/api/roles/" + id), su, null))).isEqualTo(404);
        assertThat(auditActionsSinceStart()).contains("ROLE_CREATED", "ROLE_UPDATED", "ROLE_DELETED");
    }

    @Test
    void builtInRolesCanBeRenamedButNeverChangedOrDeleted() throws Exception {
        String su = superToken();
        UUID analyst = roleId("ANALYST");
        MvcResult rename = send(put("/api/roles/" + analyst), su, Map.of("name", "Case analyst", "description", "Prepares cases"));
        assertThat(status(rename)).isEqualTo(200);
        assertThat(body(rename).get("name").asText()).isEqualTo("Case analyst");
        assertThat(body(rename).get("permissions")).hasSize(8);

        assertThat(status(send(put("/api/roles/" + analyst), su, Map.of("permissions", List.of("CASE_CREATE"))))).isEqualTo(409);
        assertThat(status(send(put("/api/roles/" + analyst), su, Map.of("permissions", body(rename).get("permissions"))))).as("same permissions is fine").isEqualTo(200);
        assertThat(status(send(delete("/api/roles/" + analyst), su, null))).isEqualTo(409);
        assertThat(status(send(delete("/api/roles/" + roleId("SUPER_ADMIN")), su, null))).isEqualTo(409);
    }

    // ---- own password ---------------------------------------------------------------------------------

    @Test
    void changingYourPasswordChecksTheOldOneAndEndsEverySession() throws Exception {
        String token = tokenFor(ownerId, OWNER, "CASE_CREATE");
        String otherSession = tokenFor(ownerId, OWNER, "CASE_CREATE");

        MvcResult wrong = send(put("/api/me/password"), token, Map.of("currentPassword", "Wrong-Password-1!", "newPassword", STRONG));
        assertThat(status(wrong)).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM auth.admins", Integer.class)).as("counts as a failed attempt").isEqualTo(1);
        assertThat(status(send(put("/api/me/password"), token, Map.of("currentPassword", PASSWORD, "newPassword", "password")))).isEqualTo(400);
        assertThat(status(send(put("/api/me/password"), token, Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD)))).as("same password").isEqualTo(400);
        assertThat(status(send(put("/api/me/password"), null, Map.of("currentPassword", PASSWORD, "newPassword", STRONG)))).isEqualTo(401);

        assertThat(status(send(put("/api/me/password"), token, Map.of("currentPassword", PASSWORD, "newPassword", STRONG)))).isEqualTo(204);
        assertThat(status(send(get("/api/me"), token, null))).as("this session").isEqualTo(401);
        assertThat(status(send(get("/api/me"), otherSession, null))).as("other sessions").isEqualTo(401);
        assertThat(status(send(post("/api/auth/login"), null, Map.of("email", OWNER, "password", PASSWORD)))).as("old password").isEqualTo(401);
        assertThat(status(send(post("/api/auth/login"), null, Map.of("email", OWNER, "password", STRONG)))).as("new password").isEqualTo(200);
        assertThat(auditActionsSinceStart()).contains("PASSWORD_CHANGE_FAILED", "PASSWORD_CHANGED");
    }

    @Test
    void guessingThePasswordThroughTheChangeEndpointLocksTheAccount() throws Exception {
        String token = tokenFor(ownerId, OWNER, "CASE_CREATE");
        for (int i = 0; i < 4; i++) {
            assertThat(status(send(put("/api/me/password"), token, Map.of("currentPassword", "Wrong-Password-" + i, "newPassword", STRONG)))).isEqualTo(401);
        }
        MvcResult locked = send(put("/api/me/password"), token, Map.of("currentPassword", "Wrong-Password-9", "newPassword", STRONG));
        assertThat(status(locked)).isEqualTo(401); // the fifth failure locks the account; the answer stays generic
        MvcResult next = send(put("/api/me/password"), token, Map.of("currentPassword", PASSWORD, "newPassword", STRONG));
        assertThat(status(next)).isEqualTo(423);
        assertThat(Long.parseLong(next.getResponse().getHeader("Retry-After"))).isPositive();
    }

    // ---- audit log --------------------------------------------------------------------------------------

    @Test
    void theAuditLogCanBeFilteredPagedAndNeverContainsSecrets() throws Exception {
        String raw = invite(superToken(), "audit@example.com", roleId("ANALYST"));
        accept(raw, STRONG);
        send(post("/api/auth/login"), null, Map.of("email", OWNER, "password", "Wrong-Password-1!"));
        send(post("/api/admins/" + adminId("audit@example.com") + "/disable"), superToken(), null);

        String reader = tokenFor(ownerId, OWNER, "AUDIT_READ");
        String from = testStart.toString();

        JsonNode all = body(send(get("/api/audit-log?from=" + from + "&size=200"), reader, null));
        assertThat(all.get("total").asInt()).isGreaterThanOrEqualTo(4);
        List<Instant> times = new java.util.ArrayList<>();
        all.get("items").forEach(i -> times.add(Instant.parse(i.get("at").asText())));
        assertThat(times).as("newest first").isSortedAccordingTo(java.util.Comparator.reverseOrder());

        assertThat(body(send(get("/api/audit-log?from=" + from + "&action=ADMIN_INVITED"), reader, null)).get("total").asInt()).isEqualTo(1);
        assertThat(body(send(get("/api/audit-log?from=" + from + "&entity=admin&action=ADMIN_DISABLED"), reader, null)).get("total").asInt()).isEqualTo(1);
        assertThat(body(send(get("/api/audit-log?from=" + from + "&actor=" + OWNER.toUpperCase()), reader, null)).get("total").asInt()).as("by email, any case").isGreaterThanOrEqualTo(2);
        assertThat(body(send(get("/api/audit-log?from=" + from + "&actor=" + ownerId), reader, null)).get("total").asInt()).as("by id").isGreaterThanOrEqualTo(2);
        assertThat(body(send(get("/api/audit-log?to=" + testStart.minusSeconds(3600)), reader, null)).get("items")).isEmpty();
        assertThat(body(send(get("/api/audit-log?from=" + from + "&size=2&page=1"), reader, null)).get("items")).hasSize(2);
        assertThat(body(send(get("/api/audit-log?size=100000"), reader, null)).get("size").asInt()).as("size is capped").isEqualTo(200);
        assertThat(status(send(get("/api/audit-log?from=yesterday"), reader, null))).isEqualTo(400);

        // No row anywhere holds a password, a hash or a link token.
        String dump = jdbc.queryForList("SELECT concat_ws(' ', action, actor_email, entity_id, before::text, after::text)"
                + " FROM auth.audit_log", String.class).toString();
        assertThat(dump).doesNotContain(STRONG).doesNotContain(PASSWORD).doesNotContain(raw).doesNotContain("argon2")
                .doesNotContain("Wrong-Password");
    }
}
