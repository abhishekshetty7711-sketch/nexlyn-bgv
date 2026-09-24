package com.nexlyn.bgv.auth.internal.security;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.internal.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Authenticates a request from its {@code Authorization: Bearer <access token>} header.
 *
 * <p>A token is only honoured if its signature, issuer, type and expiry are valid <em>and</em> its
 * session is still alive (not logged out, not revoked, not reuse-killed). Anything else leaves the
 * request unauthenticated, which the security chain then answers with 401. Never logs the token.
 *
 * <p>Deliberately not a Spring bean: registering it as one would also add it to the servlet filter
 * chain a second time. It is created inside {@link SecurityConfig}.
 */
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwt;
    private final SessionService sessions;

    JwtAuthenticationFilter(JwtService jwt, SessionService sessions) {
        this.jwt = jwt;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            authenticate(header.substring(BEARER.length()).trim());
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            JwtService.AccessClaims claims = jwt.parseAccessToken(token);
            if (!sessions.isSessionActive(claims.sessionId())) {
                return;
            }
            AdminPrincipal principal = new AdminPrincipal(claims.adminId(), claims.email(), claims.sessionId(),
                    Set.copyOf(claims.roles()), Set.copyOf(claims.permissions()));
            // Authorities are the permissions, so checks read hasAuthority('CASE_CREATE').
            var authorities = claims.permissions().stream().map(SimpleGrantedAuthority::new).toList();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.copyOf(authorities)));
            SecurityContextHolder.setContext(context);
        } catch (JwtService.InvalidTokenException e) {
            SecurityContextHolder.clearContext();
        }
    }
}
