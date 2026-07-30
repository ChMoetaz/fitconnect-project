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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test of the core happy path, driven through the real HTTP layer
 * (MockMvc) against the full Spring context on in-memory H2:
 *
 * <pre>register → login → onboarding → generate a training plan (Gemini mocked)</pre>
 *
 * <p>Only the outbound Gemini HTTP call is mocked (via a {@link MockBean} on the autoconfigured
 * {@link WebClient.Builder}); everything else — JWT issuance/verification, the ownership check,
 * JPA persistence, the double JSON parse — runs for real. This is what makes the test meaningful:
 * generation only works if onboarding actually persisted a profile first.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegisterToPlanFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    // Only the WebClient.Builder is a Spring bean (autoconfigured); replacing it is enough to
    // divert AiTrainingPlanService away from any real Gemini call. The rest of the fluent chain
    // is stubbed with plain Mockito mocks — they are not beans, so they must NOT be @MockBean.
    @MockBean
    private WebClient.Builder webClientBuilder;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void stubGemini() {
        WebClient webClient = org.mockito.Mockito.mock(WebClient.class);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = org.mockito.Mockito.mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = org.mockito.Mockito.mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

        String planJson = "{\"title\":\"Beginner Strength\",\"description\":\"A simple starter plan.\","
                + "\"exercises\":[{\"name\":\"Squat\",\"sets\":3,\"repetitions\":10},"
                + "{\"name\":\"Push Up\",\"sets\":3,\"repetitions\":12}]}";
        String escaped = planJson.replace("\"", "\\\"");
        String envelope = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escaped + "\"}]}}]}";

        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(envelope));
    }

    @Test
    void fullFlow_registerLoginOnboardGenerate() throws Exception {
        String email = "flow@fitconnect.test";
        String credentials = "{\"email\":\"" + email + "\",\"password\":\"secret123\"}";

        // 1. Register → 201 + accessToken + userId
        MvcResult registerResult = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();
        JsonNode registerBody = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        long userId = registerBody.get("userId").asLong();

        // 2. Login → 200 + a fresh token (this is the token we use for the protected calls)
        MvcResult loginResult = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        String bearer = "Bearer " + token;

        // 3. Onboarding → 200 (persists the UserProfile that generation depends on)
        String onboarding = "{\"fitnessGoal\":\"MUSCLE_GAIN\",\"fitnessLevel\":\"BEGINNER\","
                + "\"trainingFrequency\":3,\"sportTypeName\":\"Strength\"}";
        mockMvc.perform(post("/api/users/" + userId + "/onboarding")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(onboarding))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitnessGoal").value("MUSCLE_GAIN"));

        // 4. Generate a plan → 201, assembled from the (mocked) Gemini response
        mockMvc.perform(post("/api/users/" + userId + "/training-plans")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationWeeks\":6}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Beginner Strength"))
                .andExpect(jsonPath("$.duration").value(6))
                .andExpect(jsonPath("$.exercises.length()").value(2))
                .andExpect(jsonPath("$.exercises[0].name").value("Squat"));

        // 5. The plan is now readable back through the list endpoint (fetch-join, no lazy exception)
        MvcResult listResult = mockMvc.perform(get("/api/users/" + userId + "/training-plans")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        JsonNode plans = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(plans.get(0).get("exercises")).hasSize(2);
    }
}