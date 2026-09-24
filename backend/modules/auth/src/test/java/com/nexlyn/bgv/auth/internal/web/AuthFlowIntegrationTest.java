package com.nexlyn.bgv.auth.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthTestApplication;
import com.nexlyn.bgv.auth.internal.security.JwtService;
import com.nexlyn.bgv.auth.internal.service.SessionService;
import com.nexlyn.bgv.auth.internal.service.TotpService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The whole {@code /api/auth} journey over real HTTP against a real PostgreSQL: enrolment, later
 * logins, backup codes, code replay, lockout, refresh rotation and theft detection, logout and
 * session limits (CLAUDE.md {@literal §9.1}, {@literal §11.4}).
 */
@Testcontainers
@SpringBootTest(classes = AuthTestApplication.class)
@AutoConfigureMockMvc
@Import(AuthFlowIntegrationTest.Events.class)
@TestPropertySource(properties = {
        "nexlyn.auth.bootstrap.email=owner@example.com",
        "nexlyn.auth.bootstrap.password=Tr1cky-Orange-Kettle",
        "nexlyn.auth.rate-limit.ip-requests=100000",
        "nexlyn.auth.rate-limit.email-attempts=100000",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.open-in-view=false"
})
class AuthFlowIntegrationTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "Tr1cky-Orange-Kettle";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Component
    static class Events {
        final List<AuditEvent> all = new CopyOnWriteArrayList<>();

        @EventListener
        void on(AuditEvent event) {
            all.add(event);
        }

        List<String> actions() {
            return all.stream().map(AuditEvent::action).toList();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TotpService totp;
    @Autowired JwtService jwt;
    @Autowired SessionService sessions;
    @Autowired Events events;

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM auth.refresh_tokens");
        jdbc.update("DELETE FROM auth.backup_codes");
        jdbc.update("DELETE FROM auth.totp_secrets");
        jdbc.update("UPDATE auth.admins SET mfa_enabled = false, failed_attempts = 0, lockout_count = 0,"
                + " locked_until = NULL, status = 'ACTIVE', last_login_at = NULL");
        events.all.clear();
    }

    // ---- helpers ------------------------------------------------------------------------

    private MvcResult call(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
    }

    private MockHttpServletRequestBuilder postJson(String path, Object body) throws Exception {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult login(String email, String password) throws Exception {
        return call(postJson("/api/auth/login", Map.of("email", email, "password", password)));
    }

    private MvcResult verify(String challenge, String code) throws Exception {
        return call(postJson("/api/auth/2fa/verify", Map.of("challengeToken", challenge, "code", code)));
    }

    /** A valid authenticator code for the current time step, shifted by {@code stepOffset}. */
    private String codeFor(String secret, long stepOffset) {
        return totp.codeForStep(secret, totp.stepAt(Instant.now()) + stepOffset);
    }

    /** A signed-in admin who has just finished 2FA enrolment. */
    private record Session(String secret, JsonNode tokens, MockHttpServletResponse response, List<String> backupCodes) {
        String refreshCookie() {
            return response.getCookie(AuthCookies.REFRESH_COOKIE).getValue();
        }

        String csrf() {
            return response.getCookie(AuthCookies.CSRF_COOKIE).getValue();
        }
    }

    private Session enroll() throws Exception {
        JsonNode challenge = body(login(EMAIL, PASSWORD));
        assertThat(challenge.get("status").asText()).isEqualTo("2FA_SETUP_REQUIRED");
        String token = challenge.get("challengeToken").asText();

        MvcResult setup = call(postJson("/api/auth/2fa/setup", Map.of("challengeToken", token)));
        assertThat(setup.getResponse().getStatus()).isEqualTo(200);
        String secret = body(setup).get("secret").asText();

        MvcResult confirm = call(postJson("/api/auth/2fa/confirm",
                Map.of("challengeToken", token, "code", codeFor(secret, 0))));
        assertThat(confirm.getResponse().getStatus()).isEqualTo(200);
        JsonNode tokens = body(confirm);
        List<String> codes = new ArrayList<>();
        tokens.get("backupCodes").forEach(n -> codes.add(n.asText()));
        return new Session(secret, tokens, confirm.getResponse(), codes);
    }

    private MockHttpServletRequestBuilder refreshWith(String refreshCookie, String csrfCookie, String csrfHeader) {
        MockHttpServletRequestBuilder request = post("/api/auth/refresh");
        if (refreshCookie != null) {
            request.cookie(new Cookie(AuthCookies.REFRESH_COOKIE, refreshCookie));
        }
        if (csrfCookie != null) {
            request.cookie(new Cookie(AuthCookies.CSRF_COOKIE, csrfCookie));
        }
        if (csrfHeader != null) {
            request.header(AuthCookies.CSRF_HEADER, csrfHeader);
        }
        return request;
    }

    private MvcResult refresh(String refreshCookie, String csrf) throws Exception {
        return call(refreshWith(refreshCookie, csrf, csrf));
    }

    private String errorCode(MvcResult result) throws Exception {
        return body(result).get("code").asText();
    }

    // ---- first login: enrolment ------------------------------------------------------------

    @Test
    void firstLoginForcesEnrolmentAndConfirmingCompletesTheLogin() throws Exception {
        String challenge = body(login(EMAIL, PASSWORD)).get("challengeToken").asText();
        String secret = body(call(postJson("/api/auth/2fa/setup", Map.of("challengeToken", challenge)))).get("secret").asText();
        assertThat(secret).matches("[A-Z2-7]{32}");

        MvcResult wrong = call(postJson("/api/auth/2fa/confirm", Map.of("challengeToken", challenge, "code", "000000")));
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(wrong)).isEqualTo("INVALID_CODE");
        assertThat(jdbc.queryForObject("SELECT mfa_enabled FROM auth.admins", Boolean.class)).isFalse();

        MvcResult confirmed = call(postJson("/api/auth/2fa/confirm",
                Map.of("challengeToken", challenge, "code", codeFor(secret, 0))));
        MockHttpServletResponse response = confirmed.getResponse();
        JsonNode tokens = body(confirmed);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresInSeconds").asLong()).isEqualTo(900);
        assertThat(tokens.get("backupCodes")).hasSize(10);
        tokens.get("backupCodes").forEach(c -> assertThat(c.asText()).matches("[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}"));

        // The refresh cookie is httpOnly + Secure + SameSite=Strict and only sent to /api/auth;
        // the CSRF cookie is readable by the page's script.
        List<String> setCookies = response.getHeaders("Set-Cookie");
        String refreshCookie = setCookies.stream().filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow();
        String csrfCookie = setCookies.stream().filter(c -> c.startsWith("csrf_token=")).findFirst().orElseThrow();
        assertThat(refreshCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
        assertThat(csrfCookie).contains("Secure", "SameSite=Strict", "Path=/").doesNotContain("HttpOnly");

        // The access token carries the admin's roles and all 21 permissions of SUPER_ADMIN.
        JwtService.AccessClaims claims = jwt.parseAccessToken(tokens.get("accessToken").asText());
        assertThat(claims.email()).isEqualTo(EMAIL);
        assertThat(claims.roles()).containsExactly("SUPER_ADMIN");
        assertThat(claims.permissions()).hasSize(21).contains("USER_MANAGE", "AUDIT_READ", "CASE_CREATE");
        assertThat(sessions.isSessionActive(claims.sessionId())).isTrue();

        // What is stored: 2FA on, secret encrypted, backup codes hashed, refresh token hashed.
        assertThat(jdbc.queryForObject("SELECT mfa_enabled FROM auth.admins", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM auth.admins", Boolean.class)).isTrue();
        String stored = jdbc.queryForObject("SELECT secret_encrypted FROM auth.totp_secrets", String.class);
        assertThat(stored).doesNotContain(secret);
        assertThat(jdbc.queryForList("SELECT code_hash FROM auth.backup_codes", String.class))
                .hasSize(10).allMatch(h -> h.startsWith("$argon2id$"));
        assertThat(jdbc.queryForList("SELECT token_hash FROM auth.refresh_tokens", String.class))
                .hasSize(1).allMatch(h -> h.matches("[0-9a-f]{64}"))
                .noneMatch(h -> h.equals(response.getCookie("refresh_token").getValue()));
        assertThat(events.actions()).contains("TWO_FACTOR_FAILED", "TWO_FACTOR_ENABLED", "LOGIN_SUCCESS");
    }

    @Test
    void enrolmentCanBeRestartedUntilConfirmedButNeverOnceConfirmed() throws Exception {
        String challenge = body(login(EMAIL, PASSWORD)).get("challengeToken").asText();
        String first = body(call(postJson("/api/auth/2fa/setup", Map.of("challengeToken", challenge)))).get("secret").asText();
        String second = body(call(postJson("/api/auth/2fa/setup", Map.of("challengeToken", challenge)))).get("secret").asText();
        assertThat(second).isNotEqualTo(first);

        // A code for the abandoned first secret no longer works.
        MvcResult stale = call(postJson("/api/auth/2fa/confirm", Map.of("challengeToken", challenge, "code", codeFor(first, 0))));
        assertThat(stale.getResponse().getStatus()).isEqualTo(401);

        // Once enrolled, the password step now asks for a code and can no longer restart enrolment.
        call(postJson("/api/auth/2fa/confirm", Map.of("challengeToken", challenge, "code", codeFor(second, 0))));
        assertThat(body(login(EMAIL, PASSWORD)).get("status").asText()).isEqualTo("2FA_REQUIRED");
        MvcResult again = call(postJson("/api/auth/2fa/setup", Map.of("challengeToken", challenge)));
        assertThat(again.getResponse().getStatus()).isEqualTo(401);
    }

    // ---- later logins ---------------------------------------------------------------------

    @Test
    void laterLoginsNeedAnAuthenticatorCodeThatWorksOnlyOnce() throws Exception {
        Session enrolled = enroll();
        jdbc.update("UPDATE auth.totp_secrets SET last_used_step = NULL"); // as if enrolled a while ago

        JsonNode step1 = body(login(EMAIL, PASSWORD));
        assertThat(step1.get("status").asText()).isEqualTo("2FA_REQUIRED");
        String code = codeFor(enrolled.secret(), 0);

        MvcResult ok = verify(step1.get("challengeToken").asText(), code);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(ok).has("backupCodes")).as("backup codes are only shown at enrolment").isFalse();
        assertThat(ok.getResponse().getCookie("refresh_token")).isNotNull();

        // The very same code, even with a fresh and valid challenge, is refused (replay protection).
        String challenge2 = body(login(EMAIL, PASSWORD)).get("challengeToken").asText();
        MvcResult replay = verify(challenge2, code);
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(replay)).isEqualTo("INVALID_CODE");
    }

    @Test
    void aBackupCodeWorksOnceAndIgnoresCaseAndDashes() throws Exception {
        Session enrolled = enroll();
        String backup = enrolled.backupCodes().get(0);

        String challenge = body(login(EMAIL, PASSWORD)).get("challengeToken").asText();
        MvcResult used = verify(challenge, backup.toLowerCase().replace("-", " "));
        assertThat(used.getResponse().getStatus()).isEqualTo(200);
        assertThat(events.actions()).contains("BACKUP_CODE_USED");

        String challenge2 = body(login(EMAIL, PASSWORD)).get("challengeToken").asText();
        assertThat(verify(challenge2, backup).getResponse().getStatus()).as("second use").isEqualTo(401);
        assertThat(verify(challenge2, enrolled.backupCodes().get(1)).getResponse().getStatus())
                .as("a different backup code still works").isEqualTo(200);
    }

    @Test
    void wrongCodesLockTheAccountAndACompletedLoginResetsTheCounters() throws Exception {
        Session enrolled = enroll();
        jdbc.update("UPDATE auth.totp_secrets SET last_used_step = NULL");

        // Two wrong codes, then a right one: the counters go back to zero.
        String challenge = body(login(EMAIL, PASSWORD)).get("challengeToken").asText();
        verify(challenge, "111111");
        verify(challenge, "222222");
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM auth.admins", Integer.class)).isEqualTo(2);
        assertThat(verify(challenge, codeFor(enrolled.secret(), 0)).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM auth.admins", Integer.class)).isZero();

        // Five wrong codes lock the account, even against the correct code afterwards.
        String challenge2 = body(login(EMAIL, PASSWORD)).get("challengeToken").asText();
        for (int i = 0; i < 4; i++) {
            assertThat(verify(challenge2, "333333").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult locked = verify(challenge2, "333333");
        assertThat(locked.getResponse().getStatus()).isEqualTo(423);
        assertThat(errorCode(locked)).isEqualTo("ACCOUNT_LOCKED");
        assertThat(Long.parseLong(locked.getResponse().getHeader("Retry-After"))).isBetween(1L, 900L);
        assertThat(events.actions()).contains("ACCOUNT_LOCKED");

        assertThat(verify(challenge2, codeFor(enrolled.secret(), 1)).getResponse().getStatus()).isEqualTo(423);
        assertThat(login(EMAIL, PASSWORD).getResponse().getStatus()).as("password step is locked too").isEqualTo(423);
    }

    @Test
    void challengesAreOnlyAcceptedForTheirOwnPurpose() throws Exception {
        String setupChallenge = body(login(EMAIL, PASSWORD)).get("challengeToken").asText();
        MvcResult wrongPurpose = verify(setupChallenge, "123456");
        assertThat(wrongPurpose.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(wrongPurpose)).isEqualTo("INVALID_CHALLENGE");

        for (String path : List.of("/api/auth/2fa/setup", "/api/auth/2fa/confirm", "/api/auth/2fa/verify")) {
            MvcResult garbage = call(postJson(path, Map.of("challengeToken", "not-a-token", "code", "123456")));
            assertThat(garbage.getResponse().getStatus()).as(path).isEqualTo(401);
        }
        // An access token is not a challenge.
        Session enrolled = enroll();
        MvcResult accessAsChallenge = verify(enrolled.tokens().get("accessToken").asText(), "123456");
        assertThat(accessAsChallenge.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() throws Exception {
        MvcResult wrong = login(EMAIL, "Wrong-Password-1!");
        MvcResult unknown = login("nobody@example.com", "Wrong-Password-1!");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401).isEqualTo(unknown.getResponse().getStatus());
        assertThat(wrong.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        assertThat(errorCode(wrong)).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void badInputGets400WithoutEchoingTheRejectedValues() throws Exception {
        MvcResult blank = call(postJson("/api/auth/login", Map.of("email", "", "password", "hunter2-secret")));
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(blank)).isEqualTo("VALIDATION_FAILED");
        assertThat(body(blank).get("fieldErrors").get(0).get("field").asText()).isEqualTo("email");
        assertThat(blank.getResponse().getContentAsString()).doesNotContain("hunter2-secret");

        MvcResult malformed = call(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"));
        assertThat(malformed.getResponse().getStatus()).isEqualTo(400);
        assertThat(call(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)).getResponse().getStatus()).isEqualTo(400);
    }

    // ---- refresh, theft detection, logout -------------------------------------------------------

    @Test
    void refreshNeedsTheCsrfHeaderAndRotatesTheToken() throws Exception {
        Session s = enroll();

        assertThat(call(refreshWith(s.refreshCookie(), s.csrf(), null)).getResponse().getStatus())
                .as("no CSRF header").isEqualTo(403);
        assertThat(call(refreshWith(s.refreshCookie(), s.csrf(), "something-else")).getResponse().getStatus())
                .as("CSRF header does not match cookie").isEqualTo(403);
        assertThat(call(refreshWith(s.refreshCookie(), null, "x")).getResponse().getStatus())
                .as("no CSRF cookie").isEqualTo(403);
        assertThat(errorCode(call(refreshWith(s.refreshCookie(), s.csrf(), null)))).isEqualTo("CSRF_FAILED");

        MvcResult rotated = refresh(s.refreshCookie(), s.csrf());
        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(rotated).get("accessToken").asText()).isNotBlank();
        assertThat(rotated.getResponse().getCookie("refresh_token").getValue()).isNotEqualTo(s.refreshCookie());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.refresh_tokens WHERE revoked_at IS NOT NULL"
                + " AND replaced_by IS NOT NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void refreshWithoutACookieFailsAndClearsTheCookies() throws Exception {
        Session s = enroll();
        MvcResult none = call(refreshWith(null, s.csrf(), s.csrf()));
        assertThat(none.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(none)).isEqualTo("INVALID_REFRESH_TOKEN");
        assertThat(none.getResponse().getHeaders("Set-Cookie")).anyMatch(c -> c.startsWith("refresh_token=") && c.contains("Max-Age=0"));

        assertThat(refresh("made-up-token", s.csrf()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aStolenRefreshTokenThatComesBackKillsTheWholeSession() throws Exception {
        Session s = enroll();
        String r1 = s.refreshCookie();
        MvcResult second = refresh(r1, s.csrf());
        String r2 = second.getResponse().getCookie("refresh_token").getValue();
        String csrf2 = second.getResponse().getCookie("csrf_token").getValue();

        // Presenting r1 again a moment later is treated as two tabs refreshing at once: refused, session alive.
        assertThat(refresh(r1, csrf2).getResponse().getStatus()).isEqualTo(401);
        MvcResult third = refresh(r2, csrf2);
        assertThat(third.getResponse().getStatus()).as("session survives a double refresh").isEqualTo(200);
        String r3 = third.getResponse().getCookie("refresh_token").getValue();
        UUID sid = jwt.parseAccessToken(body(third).get("accessToken").asText()).sessionId();
        assertThat(sessions.isSessionActive(sid)).isTrue();

        // Later, the already-used r1 shows up again: it must have leaked. The whole session is revoked.
        jdbc.update("UPDATE auth.refresh_tokens SET revoked_at = now() - interval '1 minute' WHERE replaced_by IS NOT NULL");
        assertThat(refresh(r1, csrf2).getResponse().getStatus()).isEqualTo(401);
        assertThat(events.actions()).contains("REFRESH_TOKEN_REUSE_DETECTED");
        assertThat(sessions.isSessionActive(sid)).isFalse();
        assertThat(refresh(r3, csrf2).getResponse().getStatus()).as("the legitimate newest token is dead too").isEqualTo(401);
    }

    @Test
    void logoutEndsTheSessionAndClearsTheCookies() throws Exception {
        Session s = enroll();
        UUID sid = jwt.parseAccessToken(s.tokens().get("accessToken").asText()).sessionId();

        MockHttpServletRequestBuilder noCsrf = post("/api/auth/logout").cookie(new Cookie("refresh_token", s.refreshCookie()));
        assertThat(call(noCsrf).getResponse().getStatus()).isEqualTo(403);
        assertThat(sessions.isSessionActive(sid)).isTrue();

        MvcResult out = call(post("/api/auth/logout")
                .cookie(new Cookie("refresh_token", s.refreshCookie()), new Cookie("csrf_token", s.csrf()))
                .header(AuthCookies.CSRF_HEADER, s.csrf()));
        assertThat(out.getResponse().getStatus()).isEqualTo(204);
        assertThat(out.getResponse().getHeaders("Set-Cookie")).allMatch(c -> c.contains("Max-Age=0"));
        assertThat(sessions.isSessionActive(sid)).isFalse();
        assertThat(refresh(s.refreshCookie(), s.csrf()).getResponse().getStatus()).isEqualTo(401);
        assertThat(events.actions()).contains("LOGOUT");
    }

    @Test
    void idleAndAbsoluteSessionLimitsAreEnforced() throws Exception {
        Session idle = enroll();
        jdbc.update("UPDATE auth.refresh_tokens SET expires_at = now() - interval '1 second'");
        assertThat(refresh(idle.refreshCookie(), idle.csrf()).getResponse().getStatus()).as("idle for 30+ minutes").isEqualTo(401);

        jdbc.update("DELETE FROM auth.refresh_tokens");
        jdbc.update("UPDATE auth.totp_secrets SET last_used_step = NULL");
        String challenge = body(login(EMAIL, PASSWORD)).get("challengeToken").asText();
        MvcResult again = verify(challenge, idle.backupCodes().get(0));
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        String refreshCookie = again.getResponse().getCookie("refresh_token").getValue();
        String csrf = again.getResponse().getCookie("csrf_token").getValue();
        jdbc.update("UPDATE auth.refresh_tokens SET family_started_at = now() - interval '13 hours'");
        assertThat(refresh(refreshCookie, csrf).getResponse().getStatus()).as("logged in for 12+ hours").isEqualTo(401);
    }

    @Test
    void aRefreshTokenIsCappedAtTheTwelveHourSessionEnd() throws Exception {
        Session s = enroll();
        // 11h50m into the session: the next refresh token may only live for the remaining 10 minutes.
        jdbc.update("UPDATE auth.refresh_tokens SET family_started_at = now() - interval '11 hours 50 minutes'");
        assertThat(refresh(s.refreshCookie(), s.csrf()).getResponse().getStatus()).isEqualTo(200);
        Long remainingMinutes = jdbc.queryForObject("SELECT round(extract(epoch FROM (expires_at - now())) / 60)::bigint"
                + " FROM auth.refresh_tokens WHERE revoked_at IS NULL", Long.class);
        assertThat(remainingMinutes).isBetween(9L, 10L);
    }

    @Test
    void aDisabledAdminCannotRefreshAndLosesAllSessions() throws Exception {
        Session s = enroll();
        jdbc.update("UPDATE auth.admins SET status = 'DISABLED'");
        assertThat(refresh(s.refreshCookie(), s.csrf()).getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.refresh_tokens WHERE revoked_at IS NULL", Integer.class)).isZero();
    }
}
