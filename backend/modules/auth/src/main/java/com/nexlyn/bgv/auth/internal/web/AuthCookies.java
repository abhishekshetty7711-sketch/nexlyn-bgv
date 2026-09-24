package com.nexlyn.bgv.auth.internal.web;

import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * The two auth cookies (CLAUDE.md {@literal §11.4}):
 * <ul>
 *   <li>{@code refresh_token}: httpOnly, Secure, SameSite=Strict, only sent to {@code /api/auth}</li>
 *   <li>{@code csrf_token}: readable by the page's JavaScript, which copies it into the
 *       {@code X-CSRF-Token} header (double-submit). A cross-site attacker can do neither.</li>
 * </ul>
 */
@Component
public class AuthCookies {

    public static final String REFRESH_COOKIE = "refresh_token";
    public static final String CSRF_COOKIE = "csrf_token";
    public static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String REFRESH_PATH = "/api/auth";

    private final boolean secure;

    public AuthCookies(AuthProperties properties) {
        this.secure = properties.cookie().secure();
    }

    public void write(HttpServletResponse response, String refreshToken, Instant refreshExpiresAt,
                      String csrfToken, Instant now) {
        Duration maxAge = Duration.between(now, refreshExpiresAt);
        add(response, ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true).secure(secure).sameSite("Strict").path(REFRESH_PATH).maxAge(maxAge).build());
        add(response, ResponseCookie.from(CSRF_COOKIE, csrfToken)
                .httpOnly(false).secure(secure).sameSite("Strict").path("/").maxAge(maxAge).build());
    }

    public void clear(HttpServletResponse response) {
        add(response, ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(secure).sameSite("Strict").path(REFRESH_PATH).maxAge(0).build());
        add(response, ResponseCookie.from(CSRF_COOKIE, "")
                .httpOnly(false).secure(secure).sameSite("Strict").path("/").maxAge(0).build());
    }

    /** True only if the header and the cookie are both present, non-empty and equal (constant-time). */
    public boolean csrfMatches(HttpServletRequest request) {
        String header = request.getHeader(CSRF_HEADER);
        String cookie = cookieValue(request, CSRF_COOKIE);
        if (header == null || header.isEmpty() || cookie == null || cookie.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8), cookie.getBytes(StandardCharsets.UTF_8));
    }

    public String refreshToken(HttpServletRequest request) {
        return cookieValue(request, REFRESH_COOKIE);
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
