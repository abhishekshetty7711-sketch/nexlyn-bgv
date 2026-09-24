package com.nexlyn.bgv.auth.internal.security;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuthApi;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
class AuthApiImpl implements AuthApi {

    @Override
    public Optional<AdminPrincipal> currentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AdminPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    @Override
    public AdminPrincipal requireCurrentAdmin() {
        return currentAdmin().orElseThrow(() -> new AccessDeniedException("Not signed in"));
    }
}
