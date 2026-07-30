package com.fitconnect.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT auth: no session, no Spring Security login form — just
 * JwtAuthenticationFilter, which populates the SecurityContext from the Authorization
 * header, and a whitelist of public routes.
 *
 * Public routes (no token required): register, login, and the Swagger resources.
 * Everything else requires a valid token; the "path userId == token userId" check for the
 * /api/users/{userId}/... routes is done explicitly in each controller via
 * CurrentUser.requireSelf (see that file), not here — Spring Security only knows
 * "authenticated or not", not the notion of resource ownership.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ROUTES = {
            "/api/users/register",
            "/api/users/login",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            // The WebSocket handshake (and its SockJS sub-paths) is a plain HTTP GET that cannot carry
            // an Authorization header. It is authenticated separately at the STOMP handshake by
            // JwtHandshakeInterceptor (token as ?token=<jwt>), so the servlet chain must let it through
            // — otherwise .anyRequest().authenticated() would 401 the upgrade before our interceptor runs.
            "/ws/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // The CORS preflight never sends an Authorization header: without this
                        // explicit permitAll, .anyRequest().authenticated() would block it with a 401.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ROUTES).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
