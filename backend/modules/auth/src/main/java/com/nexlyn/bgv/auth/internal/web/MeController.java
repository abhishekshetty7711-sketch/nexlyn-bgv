package com.nexlyn.bgv.auth.internal.web;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.repository.AdminRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code GET /api/me}: the signed-in admin and their permissions. The frontend builds its UI from this. */
@RestController
public class MeController {

    record MeResponse(UUID id, String email, String fullName, List<String> roles, List<String> permissions,
                      boolean mfaEnabled, Instant lastLoginAt) {
    }

    private final AdminRepository admins;

    public MeController(AdminRepository admins) {
        this.admins = admins;
    }

    @GetMapping("/api/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal AdminPrincipal principal) {
        // Name and 2FA state come from the database (the token only carries identity and permissions).
        Admin admin = admins.findById(principal.id()).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new MeResponse(admin.getId(), admin.getEmail(), admin.getFullName(),
                        principal.roles().stream().sorted().toList(),
                        principal.permissions().stream().sorted().toList(),
                        admin.isMfaEnabled(), admin.getLastLoginAt()));
    }
}
