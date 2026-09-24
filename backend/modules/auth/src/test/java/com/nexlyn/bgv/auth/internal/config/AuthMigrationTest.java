package com.nexlyn.bgv.auth.internal.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real auth migrations against a throwaway PostgreSQL and checks the seed data
 * (CLAUDE.md §11.1, §11.2) and the append-only audit log (§11.4).
 */
@Testcontainers
class AuthMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("auth")
                .locations("classpath:db/migration/auth")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static int count(String sql) throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void seedsTheFiveSystemRolesAndTwentyOnePermissions() throws SQLException {
        assertThat(count("SELECT count(*) FROM auth.roles WHERE system_role")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM auth.permissions")).isEqualTo(21);
    }

    @Test
    void grantsMatchThePermissionMatrix() throws SQLException {
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("SUPER_ADMIN", 21);
        expected.put("OPS_MANAGER", 16);
        expected.put("QC_REVIEWER", 7);
        expected.put("ANALYST", 8);
        expected.put("AUDITOR", 3);

        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            assertThat(count("SELECT count(*) FROM auth.role_permissions rp JOIN auth.roles r ON r.id = rp.role_id"
                    + " WHERE r.code = '" + e.getKey() + "'"))
                    .as("permissions granted to %s", e.getKey())
                    .isEqualTo(e.getValue());
        }
    }

    @Test
    void separationOfDutiesPermissionsAreNotGrantedToThePreparer() throws SQLException {
        // The preparer (ANALYST) must never hold approve/finalize; the auditor is read-only.
        String analystForbidden = "SELECT count(*) FROM auth.role_permissions rp JOIN auth.roles r ON r.id = rp.role_id"
                + " WHERE r.code = 'ANALYST' AND rp.permission_code IN ('REPORT_APPROVE','REPORT_FINALIZE','USER_MANAGE','ROLE_MANAGE')";
        assertThat(count(analystForbidden)).isZero();

        String auditorWrites = "SELECT count(*) FROM auth.role_permissions rp JOIN auth.roles r ON r.id = rp.role_id"
                + " WHERE r.code = 'AUDITOR' AND rp.permission_code IN ('CASE_CREATE','CASE_UPDATE','CHECK_UPDATE','PII_UNMASK')";
        assertThat(count(auditorWrites)).isZero();

        String superOnly = "SELECT count(*) FROM auth.role_permissions WHERE permission_code IN"
                + " ('SETTINGS_MANAGE','USER_MANAGE','ROLE_MANAGE','CASE_DELETE')";
        assertThat(count(superOnly)).as("only SUPER_ADMIN holds these").isEqualTo(4);
    }

    @Test
    void emailIsUniqueIgnoringCase() throws SQLException {
        String insert = "INSERT INTO auth.admins (email, full_name, password_hash) VALUES (?, 'T', 'x')";
        try (Connection c = connect()) {
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setString(1, "Owner@Example.com");
                ps.executeUpdate();
            }
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(insert)) {
                    ps.setString(1, "owner@example.com");
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("ux_admins_email");
        }
    }

    @Test
    void auditLogIsAppendOnly() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO auth.audit_log (action, actor_email) VALUES ('TEST', 'a@b.c')");

            assertThatThrownBy(() -> s.executeUpdate("UPDATE auth.audit_log SET action = 'TAMPERED'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
            assertThatThrownBy(() -> s.executeUpdate("DELETE FROM auth.audit_log"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
            assertThatThrownBy(() -> s.executeUpdate("TRUNCATE auth.audit_log"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
        }
        assertThat(count("SELECT count(*) FROM auth.audit_log WHERE action = 'TEST'")).isEqualTo(1);
    }
}
