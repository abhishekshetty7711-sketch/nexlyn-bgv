package com.nexlyn.bgv.auth.internal.web;

import com.nexlyn.bgv.auth.internal.service.RoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** {@code /api/roles} and {@code /api/permissions}. Needs {@code ROLE_MANAGE}; the check lives on the service. */
@RestController
public class RoleController {

    record CreateRoleRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            Set<@NotBlank @Size(max = 50) String> permissions) {
    }

    record UpdateRoleRequest(
            @Size(max = 100) String name,
            @Size(max = 500) String description,
            Set<@NotBlank @Size(max = 50) String> permissions) {
    }

    private final RoleService roles;

    public RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping("/api/permissions")
    public List<RoleService.PermissionView> permissions() {
        return roles.listPermissions();
    }

    @GetMapping("/api/roles")
    public List<RoleService.RoleView> list() {
        return roles.list();
    }

    @GetMapping("/api/roles/{id}")
    public RoleService.RoleView get(@PathVariable UUID id) {
        return roles.get(id);
    }

    @PostMapping("/api/roles")
    public ResponseEntity<RoleService.RoleView> create(@Valid @RequestBody CreateRoleRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roles.create(body.code(), body.name(), body.description(), body.permissions()));
    }

    @PutMapping("/api/roles/{id}")
    public RoleService.RoleView update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest body) {
        return roles.update(id, body.name(), body.description(), body.permissions());
    }

    @DeleteMapping("/api/roles/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roles.delete(id);
        return ResponseEntity.noContent().build();
    }
}
