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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test of the adaptive-training-plan flow through the real HTTP layer (MockMvc) on
 * H2, with the Gemini call mocked at the {@link WebClient.Builder} level (no network).
 *
 * <p>Covers both branches of {@code POST .../training-plans/{planId}/adapt}:
 * <ul>
 *   <li>with no logged progress yet → 400 with the "log a few sessions first" message;</li>
 *   <li>after logging a {@code ProgressRecord} → 200, the SAME plan id updated in place (the
 *       plan list still holds exactly one plan, i.e. no duplicate plan was created).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdaptPlanFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

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

        // The same mocked Gemini response backs both the initial generation and the adaptation.
        String planJson = "{\"title\":\"Adapted Strength\",\"description\":\"Intensity adjusted from your progress.\","
                + "\"exercises\":[{\"name\":\"Squat\",\"sets\":4,\"repetitions\":10},"
                + "{\"name\":\"Lunge\",\"sets\":3,\"repetitions\":12}]}";
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

    private record Session(long userId, String bearer) {}

    /** register → login → onboarding, returning the ids/token needed for the plan calls. */
    private Session boot(String email) throws Exception {
        String credentials = "{\"email\":\"" + email + "\",\"password\":\"secret123\"}";
        MvcResult reg = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated()).andReturn();
        JsonNode body = objectMapper.readTree(reg.getResponse().getContentAsString());
        long userId = body.get("userId").asLong();
        String bearer = "Bearer " + body.get("accessToken").asText();

        mockMvc.perform(post("/api/users/" + userId + "/onboarding")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fitnessGoal\":\"MUSCLE_GAIN\",\"fitnessLevel\":\"BEGINNER\","
                                + "\"trainingFrequency\":3,\"sportTypeName\":\"Strength\"}"))
                .andExpect(status().isOk());
        return new Session(userId, bearer);
    }

    private long generatePlan(Session s) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/" + s.userId() + "/training-plans")
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationWeeks\":6}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("planId").asLong();
    }

    @Test
    void adapt_withoutProgressHistory_returnsBadRequest() throws Exception {
        Session s = boot("adapt-none@fitconnect.test");
        long planId = generatePlan(s);

        mockMvc.perform(post("/api/users/" + s.userId() + "/training-plans/" + planId + "/adapt")
                        .header("Authorization", s.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("progress history")));
    }

    @Test
    void adapt_afterLoggingProgress_updatesSamePlanInPlace() throws Exception {
        Session s = boot("adapt-ok@fitconnect.test");
        long planId = generatePlan(s);

        // Log one workout session so there is progress to adapt from.
        mockMvc.perform(post("/api/users/" + s.userId() + "/progress")
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-07-20\",\"completedWorkouts\":3,\"notes\":\"Kept the pace\"}"))
                .andExpect(status().isCreated());

        // Adapt → 200, SAME planId, exercises coming from the (mocked) Gemini response.
        mockMvc.perform(post("/api/users/" + s.userId() + "/training-plans/" + planId + "/adapt")
                        .header("Authorization", s.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.title").value("Adapted Strength"))
                .andExpect(jsonPath("$.exercises.length()").value(2));

        // No new plan was created — the user still has exactly one plan.
        mockMvc.perform(get("/api/users/" + s.userId() + "/training-plans")
                        .header("Authorization", s.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].planId").value(planId));
    }
}
