package com.nexlyn.bgv.auth.internal.web;

import com.nexlyn.bgv.auth.internal.service.AdminService;
import com.nexlyn.bgv.auth.internal.service.InvitationService;
import com.nexlyn.bgv.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code /api/admins/**}: managing admins and inviting new ones. Needs {@code USER_MANAGE}; the check
 * lives on the services. There is no "create admin with a password" endpoint on purpose: new admins
 * are invite-only (CLAUDE.md {@literal §11.4}).
 */
@RestController
@RequestMapping("/api/admins")
public class AdminController {

    record InviteRequest(@NotBlank @Email @Size(max = 254) String email, @NotEmpty List<UUID> roleIds) {
    }

    record UpdateAdminRequest(@Size(max = 200) String fullName, List<UUID> roleIds) {
    }

    private final AdminService admins;
    private final InvitationService invitations;

    public AdminController(AdminService admins, InvitationService invitations) {
        this.admins = admins;
        this.invitations = invitations;
    }

    @GetMapping
    public PageResponse<AdminService.AdminView> list(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "25") int size) {
        return admins.list(page, size);
    }

    @GetMapping("/{id}")
    public AdminService.AdminView get(@PathVariable UUID id) {
        return admins.get(id);
    }

    @PutMapping("/{id}")
    public AdminService.AdminView update(@PathVariable UUID id, @Valid @RequestBody UpdateAdminRequest body) {
        return admins.update(id, body.fullName(), body.roleIds());
    }

    @PostMapping("/{id}/disable")
    public AdminService.AdminView disable(@PathVariable UUID id) {
        return admins.disable(id);
    }

    @PostMapping("/{id}/enable")
    public AdminService.AdminView enable(@PathVariable UUID id) {
        return admins.enable(id);
    }

    @PostMapping("/{id}/unlock")
    public AdminService.AdminView unlock(@PathVariable UUID id) {
        return admins.unlock(id);
    }

    @PostMapping("/{id}/revoke-sessions")
    public ResponseEntity<Void> revokeSessions(@PathVariable UUID id) {
        admins.revokeSessions(id);
        return ResponseEntity.noContent().build();
    }

    // ---- invitations --------------------------------------------------------------------

    /** The link token in the response is shown once and cannot be retrieved again. */
    @PostMapping("/invitations")
    public ResponseEntity<InvitationService.IssuedInvitation> invite(@Valid @RequestBody InviteRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(invitations.invite(body.email(), body.roleIds()));
    }

    @GetMapping("/invitations")
    public List<InvitationService.PendingInvitation> pendingInvitations() {
        return invitations.pending();
    }

    @DeleteMapping("/invitations/{id}")
    public ResponseEntity<Void> revokeInvitation(@PathVariable UUID id) {
        invitations.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
