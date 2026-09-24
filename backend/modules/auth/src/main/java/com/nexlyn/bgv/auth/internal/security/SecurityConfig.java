package com.nexlyn.bgv.auth.internal.security;

import com.nexlyn.bgv.auth.internal.service.SessionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * The three authorization layers of CLAUDE.md {@literal §11.4}:
 * <ol>
 *   <li>this filter chain: everything under {@code /api/**} needs a valid bearer token, except the
 *       auth endpoints (which authenticate themselves) and {@code /actuator/health}</li>
 *   <li>method security: services use {@code @PreAuthorize("hasAuthority('...')")}</li>
 *   <li>data level: {@code CaseAccessPolicy}</li>
 * </ol>
 * The API is stateless: no server session and no cookie is used to authenticate {@code /api/**}
 * requests, so CSRF protection is not needed there. The cookie-based endpoints under
 * {@code /api/auth} do their own double-submit CSRF check.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtService jwt, SessionService sessions,
                                    ApiSecurityErrorHandler errors,
                                    @Qualifier("corsConfigurationSource") CorsConfigurationSource cors) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // The API only returns JSON, so nothing may load or frame anything from it.
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "geolocation=(), camera=(), microphone=(), payment=()")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS pre-flight; the CORS filter answers it
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/2fa/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("SETTINGS_MANAGE")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .addFilterBefore(new JwtAuthenticationFilter(jwt, sessions), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Only the frontend origin may call the API from a browser (credentials allowed so the refresh
     * cookie is sent). With no origin configured, no cross-origin access is granted at all.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${nexlyn.cors.allowed-origin:}") String allowedOrigin) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (allowedOrigin != null && !allowedOrigin.isBlank()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of(allowedOrigin.trim()));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-Token"));
            config.setExposedHeaders(List.of("Retry-After"));
            config.setAllowCredentials(true);
            config.setMaxAge(Duration.ofHours(1));
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }
}
