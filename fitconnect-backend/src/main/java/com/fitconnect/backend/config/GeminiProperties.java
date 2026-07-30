package com.fitconnect.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gemini")
@Data
public class GeminiProperties {
    private String apiKey;
    private String model;
    private String baseUrl;
}
