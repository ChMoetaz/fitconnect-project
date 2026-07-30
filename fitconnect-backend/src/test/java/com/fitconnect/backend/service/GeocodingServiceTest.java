package com.fitconnect.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitconnect.backend.config.GoogleMapsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GeocodingService}. The Google HTTP call is fully mocked at the
 * {@link WebClient} fluent-chain level (GET), so no request ever leaves the JVM. Verifies the happy
 * path parse AND — crucially — that every failure mode degrades gracefully to an empty Optional
 * (never throws): blank key/address short-circuit with no call, ZERO_RESULTS, HTTP error, malformed
 * JSON.
 */
@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    // WebClient GET fluent chain, mocked step by step.
    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    private GoogleMapsProperties properties;
    private GeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        properties = new GoogleMapsProperties();
        properties.setApiKey("test-maps-key");
        properties.setBaseUrl("http://localhost:0");
        geocodingService = new GeocodingService(properties, new ObjectMapper(), webClientBuilder);
    }

    @SuppressWarnings("unchecked")
    private void stubGeocodeReturns(Mono<String> body) {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(body);
    }

    @Test
    void geocode_happyPath_returnsLatLng() {
        stubGeocodeReturns(Mono.just(
                "{\"status\":\"OK\",\"results\":[{\"formatted_address\":\"Berlin, Germany\","
                        + "\"geometry\":{\"location_type\":\"APPROXIMATE\","
                        + "\"location\":{\"lat\":52.5200066,\"lng\":13.404954}}}]}"));

        Optional<GeocodingService.GeoPoint> point = geocodingService.geocode("Berlin");

        assertThat(point).isPresent();
        assertThat(point.get().latitude()).isEqualTo(52.5200066);
        assertThat(point.get().longitude()).isEqualTo(13.404954);
    }

    @Test
    void geocode_zeroResults_returnsEmpty() {
        stubGeocodeReturns(Mono.just("{\"status\":\"ZERO_RESULTS\",\"results\":[]}"));

        assertThat(geocodingService.geocode("asldkfjqwioe")).isEmpty();
    }

    @Test
    void geocode_httpError_returnsEmpty() {
        // bodyToMono errors (e.g. 4xx/5xx surfaced by retrieve()) → block() throws → swallowed.
        stubGeocodeReturns(Mono.error(new RuntimeException("403 Forbidden")));

        assertThat(geocodingService.geocode("Berlin")).isEmpty();
    }

    @Test
    void geocode_malformedJson_returnsEmpty() {
        stubGeocodeReturns(Mono.just("<html>not json</html>"));

        assertThat(geocodingService.geocode("Berlin")).isEmpty();
    }

    @Test
    void geocode_blankApiKey_returnsEmpty_withoutCallingWebClient() {
        properties.setApiKey("   ");

        assertThat(geocodingService.geocode("Berlin")).isEmpty();
        verify(webClientBuilder, never()).build();
    }

    @Test
    void geocode_blankAddress_returnsEmpty_withoutCallingWebClient() {
        assertThat(geocodingService.geocode("  ")).isEmpty();
        assertThat(geocodingService.geocode(null)).isEmpty();
        verify(webClientBuilder, never()).build();
    }
}
