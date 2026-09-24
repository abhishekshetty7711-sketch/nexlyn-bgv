package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthTestApplication;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.domain.AdminStatus;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import com.nexlyn.bgv.auth.internal.repository.LoginAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The password step of login and the first-start super admin, against a real PostgreSQL with the
 * real migrations. Covers CLAUDE.md {@literal §11.4}: lockout, generic failures, disabled admins.
 */
@Testcontainers
@SpringBootTest(classes = AuthTestApplication.class)
@Import(AuthServiceIntegrationTest.EventCatcher.class)
@TestPropertySource(properties = {
        "nexlyn.auth.bootstrap.email=Owner@Example.com",
        "nexlyn.auth.bootstrap.password=Tr1cky-Orange-Kettle",
        "nexlyn.auth.rate-limit.email-attempts=1000",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.open-in-view=false"
})
class AuthServiceIntegrationTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "Tr1cky-Orange-Kettle";
    static final ClientInfo CLIENT = new ClientInfo("10.1.2.3", "JUnit");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Collects published audit events so tests can assert on them. */
    @Component
    static class EventCatcher {
        final List<AuditEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(AuditEvent event) {
            events.add(event);
        }
    }

    @Autowired AuthService auth;
    @Autowired AdminRepository admins;
    @Autowired LoginAttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventCatcher catcher;

    @BeforeEach
    void resetLockState() {
        jdbc.update("UPDATE auth.admins SET failed_attempts = 0, lockout_count = 0, locked_until = NULL,"
                + " status = 'ACTIVE' WHERE lower(email) = ?", EMAIL);
        catcher.events.clear();
    }

    private LoginOutcome login(String email, String password) {
        return auth.verifyPassword(email, password, CLIENT);
    }

    @Test
    void bootstrapCreatedOneSuperAdminWithHashedPasswordAndNo2fa() {
        List<Admin> all = admins.findAll();
        assertThat(all).hasSize(1);
        Admin owner = all.get(0);
        assertThat(owner.getEmail()).isEqualTo(EMAIL);
        assertThat(owner.getPasswordHash()).startsWith("$argon2id$").doesNotContain(PASSWORD);
        assertThat(owner.isMfaEnabled()).isFalse();
        assertThat(jdbc.queryForList("SELECT r.code FROM auth.admin_roles ar JOIN auth.roles r ON r.id = ar.role_id"
                + " WHERE ar.admin_id = ?", String.class, owner.getId())).containsExactly("SUPER_ADMIN");
    }

    @Test
    void correctPasswordVerifiesAndRequires2faSetupBecauseItIsNotEnabledYet() {
        LoginOutcome outcome = login("  OWNER@example.COM ", PASSWORD);
        assertThat(outcome).isInstanceOfSatisfying(LoginOutcome.PasswordVerified.class,
                ok -> assertThat(ok.mfaEnabled()).isFalse());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameGenericOutcome() {
        assertThat(login(EMAIL, "Wrong-Password-1!")).isInstanceOf(LoginOutcome.InvalidCredentials.class);
        assertThat(login("nobody@example.com", "Wrong-Password-1!")).isInstanceOf(LoginOutcome.InvalidCredentials.class);
    }

    @Test
    void fifthWrongPasswordLocksTheAccountAndACorrectPasswordIsThenRefused() {
        for (int i = 0; i < 4; i++) {
            assertThat(login(EMAIL, "Wrong-Password-1!")).isInstanceOf(LoginOutcome.InvalidCredentials.class);
        }
        assertThat(login(EMAIL, "Wrong-Password-1!")).isInstanceOf(LoginOutcome.Locked.class);
        assertThat(catcher.events).extracting(AuditEvent::action).contains("ACCOUNT_LOCKED");

        assertThat(login(EMAIL, PASSWORD)).as("correct password while locked").isInstanceOf(LoginOutcome.Locked.class);
    }

    @Test
    void lockLiftsAfterItExpiresAndTheNextLockIsLonger() {
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "Wrong-Password-1!");
        }
        assertThat(lockoutCount()).isEqualTo(1);

        jdbc.update("UPDATE auth.admins SET locked_until = now() - interval '1 minute' WHERE lower(email) = ?", EMAIL);
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "Wrong-Password-1!");
        }
        assertThat(lockoutCount()).isEqualTo(2);
        Long minutes = jdbc.queryForObject("SELECT round(extract(epoch FROM (locked_until - now())) / 60)::bigint"
                + " FROM auth.admins WHERE lower(email) = ?", Long.class, EMAIL);
        assertThat(minutes).as("second lock is about 30 minutes").isBetween(29L, 30L);
    }

    @Test
    void aCorrectPasswordAloneDoesNotResetTheFailureCounters() {
        // Only a completed login (password + 2FA) resets them - see LoginFlowIntegrationTest.
        login(EMAIL, "Wrong-Password-1!");
        login(EMAIL, "Wrong-Password-1!");
        assertThat(login(EMAIL, PASSWORD)).isInstanceOf(LoginOutcome.PasswordVerified.class);
        Admin owner = admins.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(owner.getFailedAttempts()).isEqualTo(2);
    }

    @Test
    void disabledAdminCannotLogInAndLooksLikeAnyOtherFailure() {
        jdbc.update("UPDATE auth.admins SET status = 'DISABLED' WHERE lower(email) = ?", EMAIL);
        assertThat(login(EMAIL, PASSWORD)).isInstanceOf(LoginOutcome.InvalidCredentials.class);
        assertThat(admins.findByEmailIgnoreCase(EMAIL).orElseThrow().getStatus()).isEqualTo(AdminStatus.DISABLED);
    }

    @Test
    void everyPasswordCheckIsRecordedWithoutThePassword() {
        long before = attempts.count();
        login(EMAIL, "Wrong-Password-1!");
        login(EMAIL, PASSWORD);
        assertThat(attempts.count()).isEqualTo(before + 2);
        assertThat(jdbc.queryForList("SELECT email || ip || coalesce(reason, '') FROM auth.login_attempts", String.class))
                .noneMatch(row -> row.contains("Wrong-Password") || row.contains(PASSWORD));
    }

    @Test
    void failuresArePublishedAsAuditEventsWithoutSecrets() {
        login(EMAIL, "Wrong-Password-1!");
        assertThat(catcher.events).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo("LOGIN_FAILED:BAD_PASSWORD");
            assertThat(e.actorEmail()).isEqualTo(EMAIL);
            assertThat(e.ip()).isEqualTo("10.1.2.3");
            assertThat(e.toString()).doesNotContain("Wrong-Password");
        });
    }

    private int lockoutCount() {
        return jdbc.queryForObject("SELECT lockout_count FROM auth.admins WHERE lower(email) = ?", Integer.class, EMAIL);
    }
}
