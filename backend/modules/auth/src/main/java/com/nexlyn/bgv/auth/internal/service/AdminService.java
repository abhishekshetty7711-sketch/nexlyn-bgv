package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.domain.AdminStatus;
import com.nexlyn.bgv.auth.internal.domain.Role;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import com.nexlyn.bgv.auth.internal.repository.RoleRepository;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.common.web.PageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Managing existing admins (CLAUDE.md {@literal §9.1}). Every change that alters what an admin may do
 * (roles, disable) ends their sessions at once, and every change is audited. Guard rules live in
 * {@link PrivilegeGuard}: no escalation, and the system can never be left without administrators.
 */
@Service
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class AdminService {

    public record AdminView(UUID id, String email, String fullName, AdminStatus status, boolean mfaEnabled,
                            Instant lastLoginAt, Instant lockedUntil, List<String> roles) {
    }

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminRepository admins;
    private final RoleRepository roles;
    private final SessionService sessions;
    private final PrivilegeGuard guard;
    private final AuthApi auth;
    private final ApplicationEventPublisher events;

    public AdminService(AdminRepository admins, RoleRepository roles, SessionService sessions, PrivilegeGuard guard,
                        AuthApi auth, ApplicationEventPublisher events) {
        this.admins = admins;
        this.roles = roles;
        this.sessions = sessions;
        this.guard = guard;
        this.auth = auth;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminView> list(int page, int size) {
        Page<Admin> result = admins.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("email")));
        return new PageResponse<>(result.map(AdminService::view).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminView get(UUID id) {
        return view(find(id));
    }

    @Transactional
    public AdminView update(UUID id, String fullName, List<UUID> roleIds) {
        AdminPrincipal caller = auth.requireCurrentAdmin();
        Admin admin = find(id);
        guard.assertCanManage(caller, admin.getRoles());
        Map<String, Object> before = snapshot(admin);

        if (fullName != null && !fullName.isBlank()) {
            admin.setFullName(fullName.trim());
        }
        boolean rolesChanged = false;
        if (roleIds != null) {
            Set<UUID> wanted = new LinkedHashSet<>(roleIds);
            List<Role> newRoles = roles.findAllById(wanted);
            if (wanted.isEmpty() || newRoles.size() != wanted.size()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose at least one existing role.",
                        List.of(new ApiError.FieldError("roleIds", "is invalid")), null);
            }
            guard.assertCanGrant(caller, newRoles);
            Set<UUID> current = new LinkedHashSet<>(admin.getRoles().stream().map(Role::getId).toList());
            rolesChanged = !current.equals(wanted);
            if (rolesChanged) {
                admin.getRoles().clear();
                admin.getRoles().addAll(newRoles);
            }
        }
        admins.saveAndFlush(admin);
        if (rolesChanged) {
            guard.assertAdministrable();
            sessions.endAllSessions(admin.getId()); // the old tokens still carry the old permissions
        }
        events.publishEvent(new AuditEvent("ADMIN_UPDATED", null, null, "ADMIN", admin.getId().toString(), null,
                null, null, before, snapshot(admin)));
        return view(admin);
    }

    @Transactional
    public AdminView disable(UUID id) {
        AdminPrincipal caller = auth.requireCurrentAdmin();
        if (caller.id().equals(id)) {
            throw new ApiException(ErrorCode.CONFLICT, "You cannot disable your own account.");
        }
        Admin admin = find(id);
        guard.assertCanManage(caller, admin.getRoles());
        Map<String, Object> before = snapshot(admin);
        admin.setStatus(AdminStatus.DISABLED);
        admins.saveAndFlush(admin);
        guard.assertAdministrable();
        sessions.endAllSessions(admin.getId());
        events.publishEvent(new AuditEvent("ADMIN_DISABLED", null, null, "ADMIN", admin.getId().toString(), null,
                null, null, before, snapshot(admin)));
        return view(admin);
    }

    @Transactional
    public AdminView enable(UUID id) {
        AdminPrincipal caller = auth.requireCurrentAdmin();
        Admin admin = find(id);
        guard.assertCanManage(caller, admin.getRoles());
        Map<String, Object> before = snapshot(admin);
        admin.setStatus(AdminStatus.ACTIVE);
        admins.save(admin);
        events.publishEvent(new AuditEvent("ADMIN_ENABLED", null, null, "ADMIN", admin.getId().toString(), null,
                null, null, before, snapshot(admin)));
        return view(admin);
    }

    /** Lifts a lockout immediately (failed-attempt counters reset too). */
    @Transactional
    public AdminView unlock(UUID id) {
        AdminPrincipal caller = auth.requireCurrentAdmin();
        Admin admin = find(id);
        guard.assertCanManage(caller, admin.getRoles());
        Map<String, Object> before = snapshot(admin);
        admin.setFailedAttempts(0);
        admin.setLockoutCount(0);
        admin.setLockedUntil(null);
        admins.save(admin);
        events.publishEvent(new AuditEvent("ADMIN_UNLOCKED", null, null, "ADMIN", admin.getId().toString(), null,
                null, null, before, snapshot(admin)));
        return view(admin);
    }

    @Transactional
    public void revokeSessions(UUID id) {
        AdminPrincipal caller = auth.requireCurrentAdmin();
        Admin admin = find(id);
        guard.assertCanManage(caller, admin.getRoles());
        sessions.endAllSessions(admin.getId());
        events.publishEvent(new AuditEvent("SESSIONS_REVOKED", null, null, "ADMIN", admin.getId().toString(), null,
                null, null, null, null));
    }

    private Admin find(UUID id) {
        return admins.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Admin not found."));
    }

    static AdminView view(Admin admin) {
        return new AdminView(admin.getId(), admin.getEmail(), admin.getFullName(), admin.getStatus(),
                admin.isMfaEnabled(), admin.getLastLoginAt(), admin.getLockedUntil(),
                admin.getRoles().stream().map(Role::getCode).sorted().toList());
    }

    /** Audit snapshot: never includes the password hash or any secret. */
    private static Map<String, Object> snapshot(Admin admin) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("email", admin.getEmail());
        map.put("fullName", admin.getFullName());
        map.put("status", admin.getStatus().name());
        map.put("roles", admin.getRoles().stream().map(Role::getCode).sorted().toList());
        return map;
    }
}
