package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.domain.Invitation;
import com.nexlyn.bgv.auth.internal.domain.Role;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import com.nexlyn.bgv.auth.internal.repository.InvitationRepository;
import com.nexlyn.bgv.auth.internal.repository.RoleRepository;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Invite-only onboarding (CLAUDE.md {@literal §11.4}): a user-manager invites an email address
 * with some roles; the invitee opens the one-time link, chooses a password, then sets up 2FA. There
 * is no email sending yet, so the link token is returned once for the inviter to pass on.
 */
@Service
public class InvitationService {

    /** The raw link token exists only in this response; the database keeps just its hash. */
    public record IssuedInvitation(UUID id, String email, Instant expiresAt, String inviteToken) {
    }

    public record PendingInvitation(UUID id, String email, List<String> roles, Instant expiresAt, Instant createdAt) {
    }

    private final InvitationRepository invitations;
    private final AdminRepository admins;
    private final RoleRepository roles;
    private final PasswordHasher hasher;
    private final PasswordPolicyService policy;
    private final PrivilegeGuard guard;
    private final AuthApi auth;
    private final ApplicationEventPublisher events;
    private final AuthProperties.Invitation settings;
    private final Clock clock;

    public InvitationService(InvitationRepository invitations, AdminRepository admins, RoleRepository roles,
                             PasswordHasher hasher, PasswordPolicyService policy, PrivilegeGuard guard, AuthApi auth,
                             ApplicationEventPublisher events, AuthProperties properties, Clock clock) {
        this.invitations = invitations;
        this.admins = admins;
        this.roles = roles;
        this.hasher = hasher;
        this.policy = policy;
        this.guard = guard;
        this.auth = auth;
        this.events = events;
        this.settings = properties.invitation();
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public IssuedInvitation invite(String rawEmail, List<UUID> roleIds) {
        AdminPrincipal caller = auth.requireCurrentAdmin();
        String email = Admin.normalizeEmail(rawEmail);
        Instant now = Instant.now(clock);

        if (admins.findByEmailIgnoreCase(email).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "An admin with this email already exists.");
        }
        List<Role> granted = loadRoles(roleIds);
        guard.assertCanGrant(caller, granted);

        invitations.expirePendingForEmail(email, now); // a new link replaces any earlier one
        String token = SecureTokens.newToken();
        Invitation invitation = invitations.save(new Invitation(email, granted.stream().map(Role::getId).toList(),
                SecureTokens.hash(token), now.plus(settings.ttl()), caller.id(), now));

        events.publishEvent(new AuditEvent("ADMIN_INVITED", null, null, "INVITATION", invitation.getId().toString(),
                null, null, null, null,
                Map.of("email", email, "roles", granted.stream().map(Role::getCode).sorted().toList(),
                        "expiresAt", invitation.getExpiresAt().toString())));
        return new IssuedInvitation(invitation.getId(), email, invitation.getExpiresAt(), token);
    }

    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional(readOnly = true)
    public List<PendingInvitation> pending() {
        return invitations.findPending(Instant.now(clock)).stream()
                .map(i -> new PendingInvitation(i.getId(), i.getEmail(),
                        roles.findAllById(i.getRoleIds()).stream().map(Role::getCode).sorted().toList(),
                        i.getExpiresAt(), i.getCreatedAt()))
                .toList();
    }

    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public void revoke(UUID invitationId) {
        Invitation invitation = invitations.findById(invitationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Invitation not found."));
        Instant now = Instant.now(clock);
        if (!invitation.isUsable(now)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Invitation not found.");
        }
        invitation.expire(now);
        events.publishEvent(new AuditEvent("INVITATION_REVOKED", null, null, "INVITATION", invitation.getId().toString(),
                null, null, null, Map.of("email", invitation.getEmail()), null));
    }

    /**
     * Public: the link token is the credential. Creates the admin with the invited roles and no 2FA
     * yet, and returns their id so the caller can start 2FA setup. Every problem with the link
     * (unknown, used, expired) gets the same answer so links cannot be probed.
     */
    @Transactional
    public UUID accept(String token, String fullName, String password) {
        Instant now = Instant.now(clock);
        Invitation invitation = invitations.findForUpdateByTokenHash(SecureTokens.hash(token))
                .filter(i -> i.isUsable(now))
                .orElseThrow(InvitationService::invalid);
        if (admins.findByEmailIgnoreCase(invitation.getEmail()).isPresent()) {
            throw invalid();
        }
        List<PasswordPolicyService.Problem> problems = policy.check(password, invitation.getEmail());
        if (!problems.isEmpty()) {
            throw new ApiException(ErrorCode.WEAK_PASSWORD, "The password does not meet the password rules.",
                    List.of(new ApiError.FieldError("password", problems.toString())), null);
        }
        List<Role> invitedRoles = roles.findAllById(invitation.getRoleIds()); // a since-deleted role is dropped
        if (invitedRoles.isEmpty()) {
            throw invalid();
        }

        Admin admin = Admin.create(invitation.getEmail(), fullName.trim(), hasher.hash(password), now);
        admin.getRoles().addAll(invitedRoles);
        admins.save(admin);
        invitation.markAccepted(now);

        events.publishEvent(new AuditEvent("INVITATION_ACCEPTED", admin.getId(), admin.getEmail(), "ADMIN",
                admin.getId().toString(), null, null, null, null,
                Map.of("roles", invitedRoles.stream().map(Role::getCode).sorted().toList(),
                        "invitedBy", String.valueOf(invitation.getInvitedBy()))));
        return admin.getId();
    }

    private List<Role> loadRoles(List<UUID> roleIds) {
        Set<UUID> unique = new LinkedHashSet<>(roleIds);
        List<Role> found = roles.findAllById(unique);
        if (unique.isEmpty() || new HashSet<>(found.stream().map(Role::getId).toList()).size() != unique.size()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose at least one existing role.",
                    List.of(new ApiError.FieldError("roleIds", "is invalid")), null);
        }
        return found;
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_INVITATION, "This invitation is invalid or has expired.");
    }
}
