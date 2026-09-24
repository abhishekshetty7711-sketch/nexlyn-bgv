package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.internal.domain.PermissionEntity;
import com.nexlyn.bgv.auth.internal.domain.Role;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import com.nexlyn.bgv.auth.internal.repository.PermissionRepository;
import com.nexlyn.bgv.auth.internal.repository.RoleRepository;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Roles and their permissions (CLAUDE.md {@literal §11.1}). The five seeded roles are system roles:
 * their permissions are fixed and they cannot be deleted; only name and description may change.
 * Custom roles are free, but nobody can give a role a permission they do not hold themselves, and a
 * change to a role ends the sessions of everyone holding it (their tokens carry the old permissions).
 */
@Service
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
public class RoleService {

    public record RoleView(UUID id, String code, String name, String description, boolean systemRole,
                           List<String> permissions, long memberCount) {
    }

    public record PermissionView(String code, String description) {
    }

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{2,49}$");

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final AdminRepository admins;
    private final SessionService sessions;
    private final PrivilegeGuard guard;
    private final AuthApi auth;
    private final ApplicationEventPublisher events;

    public RoleService(RoleRepository roles, PermissionRepository permissions, AdminRepository admins,
                       SessionService sessions, PrivilegeGuard guard, AuthApi auth, ApplicationEventPublisher events) {
        this.roles = roles;
        this.permissions = permissions;
        this.admins = admins;
        this.sessions = sessions;
        this.guard = guard;
        this.auth = auth;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<PermissionView> listPermissions() {
        return permissions.findAll().stream()
                .map(p -> new PermissionView(p.getCode(), p.getDescription()))
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();
    }

    /** Read-only, and also open to user-managers, who must see the roles to assign them. */
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE', 'USER_MANAGE')")
    @Transactional(readOnly = true)
    public List<RoleView> list() {
        Map<UUID, Long> members = memberCounts();
        return roles.findAll().stream()
                .map(r -> view(r, members.getOrDefault(r.getId(), 0L)))
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleView get(UUID id) {
        return view(find(id), memberCounts().getOrDefault(id, 0L));
    }

    @Transactional
    public RoleView create(String code, String name, String description, Set<String> permissionCodes) {
        AdminPrincipal caller = auth.requireCurrentAdmin();
        if (!CODE.matcher(code).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The role code is not valid.",
                    List.of(new ApiError.FieldError("code", "use 3-50 capital letters, digits or underscores")), null);
        }
        if (roles.findByCode(code).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "A role with this code already exists.");
        }
        Set<String> wanted = validPermissions(permissionCodes);
        guard.assertHoldsAll(caller, wanted);

        Role role = roles.saveAndFlush(Role.create(code, name.trim(), blankToNull(description), wanted));
        events.publishEvent(new AuditEvent("ROLE_CREATED", null, null, "ROLE", role.getId().toString(), null,
                null, null, null, snapshot(role)));
        return view(role, 0);
    }

    @Transactional
    public RoleView update(UUID id, String name, String description, Set<String> permissionCodes) {
        AdminPrincipal caller = auth.requireCurrentAdmin();
        Role role = find(id);
        Map<String, Object> before = snapshot(role);
        boolean permissionsChanged = false;

        if (permissionCodes != null) {
            Set<String> wanted = validPermissions(permissionCodes);
            if (!wanted.equals(role.getPermissions())) {
                if (role.isSystemRole()) {
                    throw new ApiException(ErrorCode.CONFLICT, "The permissions of a built-in role cannot be changed. "
                            + "Create a custom role instead.");
                }
                Set<String> added = new TreeSet<>(wanted);
                added.removeAll(role.getPermissions());
                guard.assertHoldsAll(caller, added);
                role.replacePermissions(wanted);
                permissionsChanged = true;
            }
        }
        if (name != null && !name.isBlank()) {
            role.setName(name.trim());
        }
        if (description != null) {
            role.setDescription(blankToNull(description));
        }
        roles.saveAndFlush(role);
        if (permissionsChanged) {
            guard.assertAdministrable();
            admins.findIdsByRoleId(role.getId()).forEach(sessions::endAllSessions);
        }
        events.publishEvent(new AuditEvent("ROLE_UPDATED", null, null, "ROLE", role.getId().toString(), null,
                null, null, before, snapshot(role)));
        return view(role, memberCounts().getOrDefault(id, 0L));
    }

    @Transactional
    public void delete(UUID id) {
        Role role = find(id);
        if (role.isSystemRole()) {
            throw new ApiException(ErrorCode.CONFLICT, "A built-in role cannot be deleted.");
        }
        if (memberCounts().getOrDefault(id, 0L) > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "This role is still assigned to admins. Change their roles first.");
        }
        Map<String, Object> before = snapshot(role);
        roles.delete(role);
        events.publishEvent(new AuditEvent("ROLE_DELETED", null, null, "ROLE", id.toString(), null,
                null, null, before, null));
    }

    // ---- helpers ------------------------------------------------------------------------

    private Role find(UUID id) {
        return roles.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Role not found."));
    }

    private Set<String> validPermissions(Set<String> codes) {
        Set<String> known = new TreeSet<>();
        permissions.findAll().forEach((PermissionEntity p) -> known.add(p.getCode()));
        Set<String> wanted = new TreeSet<>(codes == null ? Set.of() : codes);
        if (!known.containsAll(wanted)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "One or more permissions do not exist.",
                    List.of(new ApiError.FieldError("permissions", "contains an unknown permission")), null);
        }
        return wanted;
    }

    private Map<UUID, Long> memberCounts() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : admins.countMembersPerRole()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static RoleView view(Role role, long members) {
        return new RoleView(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystemRole(),
                role.getPermissions().stream().sorted().toList(), members);
    }

    private static Map<String, Object> snapshot(Role role) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", role.getCode());
        map.put("name", role.getName());
        map.put("description", role.getDescription());
        map.put("permissions", role.getPermissions().stream().sorted().toList());
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
