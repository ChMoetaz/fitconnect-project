package com.fitconnect.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test of the geocoding enrichment over real HTTP (MockMvc) on H2. The outbound Google
 * call is mocked at the {@link WebClient} level (no network), returning a fixed Berlin coordinate for
 * any address. Everything else runs for real, so it proves that creating a community group / a coach
 * actually persists lat/lng, that they surface in the response DTOs, and that the /nearby endpoints
 * filter by Haversine distance. The Maps key is overridden non-blank here (the shared test config
 * leaves it blank so all OTHER tests stay offline).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "google.maps.api-key=test-maps-key")
class GeocodingFlowIntegrationTest {

    private static final double BERLIN_LAT = 52.5200066;
    private static final double BERLIN_LNG = 13.404954;
    private static final String BERLIN_GEOCODE_JSON =
            "{\"status\":\"OK\",\"results\":[{\"geometry\":{\"location\":{"
                    + "\"lat\":" + BERLIN_LAT + ",\"lng\":" + BERLIN_LNG + "}}}]}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    // Replaces the autoconfigured builder for the whole context; GeocodingService gets this mock.
    @MockBean
    private WebClient.Builder webClientBuilder;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void stubGeocoding() {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(Function.class))).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(BERLIN_GEOCODE_JSON));
    }

    private record Account(long userId, String bearer) {}

    private Account register(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"secret123\"}";
        MvcResult result = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Account(node.get("userId").asLong(), "Bearer " + node.get("accessToken").asText());
    }

    @Test
    void creatingGroup_geocodesLocation_andNearbyFilters() throws Exception {
        Account a = register("geo-group@fitconnect.test");

        MvcResult created = mockMvc.perform(post("/api/community-groups")
                        .header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Berlin Runners\",\"location\":\"Alexanderplatz, Berlin\","
                                + "\"sportTypeName\":\"Running\"}"))
                .andExpect(status().isCreated()).andReturn();
        long groupId = objectMapper.readTree(created.getResponse().getContentAsString()).get("communityId").asLong();

        // lat/lng populated and visible in the list DTO.
        mockMvc.perform(get("/api/community-groups").header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.communityId == " + groupId + ")].latitude").value(BERLIN_LAT))
                .andExpect(jsonPath("$[?(@.communityId == " + groupId + ")].longitude").value(BERLIN_LNG));

        // Nearby the geocoded point → present; far away (Munich) → absent.
        mockMvc.perform(get("/api/community-groups/nearby")
                        .header("Authorization", a.bearer())
                        .param("lat", String.valueOf(BERLIN_LAT)).param("lng", String.valueOf(BERLIN_LNG))
                        .param("radiusKm", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.communityId == " + groupId + ")]").isNotEmpty());
        mockMvc.perform(get("/api/community-groups/nearby")
                        .header("Authorization", a.bearer())
                        .param("lat", "48.1374").param("lng", "11.5755").param("radiusKm", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.communityId == " + groupId + ")]").isEmpty());
    }

    @Test
    void creatingCoach_geocodesLocation_andNearbyFilters() throws Exception {
        Account a = register("geo-coach@fitconnect.test");

        MvcResult created = mockMvc.perform(post("/api/coaches")
                        .header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"specialization\":\"Strength\",\"experienceYears\":6,"
                                + "\"location\":\"Alexanderplatz, Berlin\",\"sportTypeNames\":[\"Running\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").value(BERLIN_LAT))
                .andExpect(jsonPath("$.longitude").value(BERLIN_LNG))
                .andReturn();
        long coachId = objectMapper.readTree(created.getResponse().getContentAsString()).get("coachId").asLong();

        mockMvc.perform(get("/api/coaches/nearby")
                        .header("Authorization", a.bearer())
                        .param("lat", String.valueOf(BERLIN_LAT)).param("lng", String.valueOf(BERLIN_LNG))
                        .param("radiusKm", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.coachId == " + coachId + ")]").isNotEmpty());
        mockMvc.perform(get("/api/coaches/nearby")
                        .header("Authorization", a.bearer())
                        .param("lat", "48.1374").param("lng", "11.5755").param("radiusKm", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.coachId == " + coachId + ")]").isEmpty());
    }
}
