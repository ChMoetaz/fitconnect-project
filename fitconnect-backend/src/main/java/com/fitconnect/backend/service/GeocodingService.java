package com.fitconnect.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitconnect.backend.config.GoogleMapsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Turns a free-text address into coordinates via the Google Geocoding API
 * ({@code {baseUrl}/geocode/json?address=...&key=...}).
 *
 * <p><b>Best-effort by design</b>: geocoding is a non-critical enrichment (it only powers the map /
 * the {@code /nearby} filter). Every failure mode — blank API key, blank address, address not found
 * (ZERO_RESULTS), invalid key / quota exceeded (HTTP error), timeout, malformed JSON — is swallowed
 * here into an empty {@link Optional} with a logged warning, so a caller ({@code createGroup},
 * {@code createCoach}) can always fall back to leaving lat/lng null instead of failing the whole
 * request for an external problem. This service therefore NEVER throws.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeocodingService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final GoogleMapsProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    /** A geocoded point. Public so callers ({@code createGroup}/{@code createCoach}) can read lat/lng. */
    public record GeoPoint(double latitude, double longitude) {}

    public Optional<GeoPoint> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Google Maps API key is missing (GOOGLE_MAPS_API_KEY) — skipping geocoding for '{}'", address);
            return Optional.empty();
        }

        try {
            String raw = webClientBuilder.baseUrl(properties.getBaseUrl()).build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/geocode/json")
                            .queryParam("address", address)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
            return parse(raw, address);
        } catch (Exception e) {
            // invalid key / quota / timeout / network — non-critical, keep lat/lng null.
            log.warn("Geocoding call failed for '{}': {} — leaving coordinates null", address, e.toString());
            return Optional.empty();
        }
    }

    private Optional<GeoPoint> parse(String raw, String address) {
        try {
            GeocodeResponse response = objectMapper.readValue(raw, GeocodeResponse.class);
            if (response == null || !"OK".equals(response.status())
                    || response.results() == null || response.results().isEmpty()) {
                log.warn("Geocoding returned no usable result for '{}' (status={})",
                        address, response != null ? response.status() : "null");
                return Optional.empty();
            }
            Location loc = response.results().get(0).geometry().location();
            return Optional.of(new GeoPoint(loc.lat(), loc.lng()));
        } catch (Exception e) {
            log.warn("Could not parse geocoding response for '{}': {} — leaving coordinates null",
                    address, e.toString());
            return Optional.empty();
        }
    }

    // Only the fields we need; ignoreUnknown so the many other Google fields don't break parsing
    // (and so it works with both Spring's lenient ObjectMapper and a plain one in unit tests).
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeocodeResponse(String status, List<Result> results) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Result(Geometry geometry) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Geometry(Location location) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Location(double lat, double lng) {}
}
