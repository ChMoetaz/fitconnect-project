package com.fitconnect.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the Google Maps Geocoding API (see {@code GeocodingService}). Bound from
 * {@code google.maps.*} in application.yml. The {@code apiKey} comes from the GOOGLE_MAPS_API_KEY
 * environment variable (same pattern as GEMINI_API_KEY / JWT_SECRET) and must never be committed —
 * a blank key simply disables geocoding (lat/lng stay null), it does not break the app.
 */
@Component
@ConfigurationProperties(prefix = "google.maps")
@Data
public class GoogleMapsProperties {
    private String apiKey;
    /** Base URL of the Maps web services; the geocoding endpoint is {@code {baseUrl}/geocode/json}. */
    private String baseUrl = "https://maps.googleapis.com/maps/api";
}
