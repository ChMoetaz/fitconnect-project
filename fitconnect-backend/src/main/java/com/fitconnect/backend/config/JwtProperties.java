package com.fitconnect.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private String secret;
    private long expirationMs;

    /**
     * Fail fast at startup rather than 500-ing on the first login: without a valid
     * secret every token issued/verified afterwards would be broken anyway.
     */
    @PostConstruct
    private void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Configure the JWT_SECRET environment variable "
                            + "(minimum 32 characters) before starting the application, following the "
                            + "same pattern as GEMINI_API_KEY.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short for HMAC-SHA256 (minimum 32 bytes / 256 bits).");
        }
    }
}
