package com.nexlyn.bgv.auth;

import java.util.Set;
import java.util.UUID;

/**
 * The signed-in admin as seen by other modules: who they are and what they may do. Built from the
 * verified access token. {@code permissions} are the authorities to check; role names are for display only.
 */
public record AdminPrincipal(UUID id, String email, UUID sessionId, Set<String> roles, Set<String> permissions) {

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
