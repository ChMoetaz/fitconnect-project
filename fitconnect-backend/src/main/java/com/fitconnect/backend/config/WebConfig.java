package com.fitconnect.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS opened up for local development (React on localhost:5173 / 3000).
 * Should be restricted in production.
 *
 * Exposed as a CorsConfigurationSource bean (rather than a WebMvcConfigurer.addCorsMappings)
 * because that is the bean type SecurityConfig.filterChain() consumes via .cors(...): the
 * Spring Security filter chain runs before the DispatcherServlet, so it is the one that must
 * handle CORS (including OPTIONS preflight requests) so the JWT token never blocks a
 * legitimate call from the frontend.
 */
@Configuration
public class WebConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
