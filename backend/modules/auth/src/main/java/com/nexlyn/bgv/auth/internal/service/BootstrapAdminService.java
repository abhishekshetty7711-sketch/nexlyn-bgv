package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.domain.Role;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import com.nexlyn.bgv.auth.internal.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Creates the first SUPER_ADMIN on first start (CLAUDE.md {@literal §11.4}). Only acts when the
 * admins table is completely empty, so it can never overwrite or add to an existing setup.
 * The new admin has 2FA off, which forces 2FA setup at the first login.
 */
@Service
public class BootstrapAdminService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);
    static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final AuthProperties.Bootstrap settings;
    private final AdminRepository admins;
    private final RoleRepository roles;
    private final PasswordHasher hasher;
    private final PasswordPolicyService policy;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BootstrapAdminService(AuthProperties properties, AdminRepository admins, RoleRepository roles,
                                 PasswordHasher hasher, PasswordPolicyService policy,
                                 ApplicationEventPublisher events, Clock clock) {
        this.settings = properties.bootstrap();
        this.admins = admins;
        this.roles = roles;
        this.hasher = hasher;
        this.policy = policy;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void bootstrapIfNeeded() {
        if (isBlank(settings.email())) {
            return;
        }
        if (admins.count() > 0) {
            log.info("Bootstrap super admin skipped: admins already exist");
            return;
        }
        if (isBlank(settings.password())) {
            log.warn("BOOTSTRAP_SUPERADMIN_EMAIL is set but BOOTSTRAP_SUPERADMIN_PASSWORD is not; no admin created");
            return;
        }
        List<PasswordPolicyService.Problem> problems = policy.check(settings.password(), settings.email());
        if (!problems.isEmpty()) {
            // Fail fast, and say which rules broke - never the password itself.
            throw new IllegalStateException("BOOTSTRAP_SUPERADMIN_PASSWORD does not meet the password policy: " + problems);
        }
        Role superAdmin = roles.findByCode(SUPER_ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("Seeded role " + SUPER_ADMIN_ROLE + " is missing"));

        Admin admin = Admin.create(settings.email(), "Super Admin", hasher.hash(settings.password()), clock.instant());
        admin.getRoles().add(superAdmin);
        admins.save(admin);
        events.publishEvent(AuditEvent.forAdmin("ADMIN_BOOTSTRAPPED", admin.getId(), admin.getEmail(), null, null));
        log.info("Bootstrap super admin created; 2FA setup will be required at first login");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
