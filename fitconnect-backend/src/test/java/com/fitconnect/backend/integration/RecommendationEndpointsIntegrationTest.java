package com.fitconnect.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard for the AI-recommendation feature (see the "Recommandations personnalisées (Gemini)"
 * section of CLAUDE.md). Two goals, both over real HTTP (MockMvc) on H2:
 *
 * <ol>
 *   <li><b>The pre-existing, non-AI endpoints are UNAFFECTED</b> — full coach list, sport-type filter,
 *       coach {@code /nearby}, full community list, community {@code /nearby} all still respond
 *       normally. Crucially, none of them route through Gemini: the test config has only a dummy
 *       {@code gemini.api-key} and no mocked {@code WebClient}, so if any of these had started
 *       depending on Gemini the call would fail — a green run proves they stayed independent.</li>
 *   <li><b>The new recommendation endpoints are guarded</b> the same way as the rest of
 *       {@code /api/users/{userId}/...}: 401 without a token, 403 cross-user, and a clean 400 (not a
 *       500) when the caller has not completed onboarding — all reached BEFORE any Gemini call, so no
 *       WebClient mock is needed here.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecommendationEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private record Account(long userId, String bearer) {}

    private Account register(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"secret123\"}";
        MvcResult result = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Account(node.get("userId").asLong(), "Bearer " + node.get("accessToken").asText());
    }

    private JsonNode createCoach(Account a, String name, String sport, String location) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"specialization\":\"Strength\","
                + "\"location\":\"" + location + "\",\"sportTypeNames\":[\"" + sport + "\"]}";
        MvcResult result = mockMvc.perform(post("/api/coaches")
                        .header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long createGroup(Account a, String name, String sport) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/community-groups")
                        .header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"sportTypeName\":\"" + sport + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("communityId").asLong();
    }

    // --- 1) existing coach endpoints unaffected -------------------------------------------------

    @Test
    void existingCoachEndpoints_stillWork() throws Exception {
        Account owner = register("reco-coach-owner@fitconnect.test");
        JsonNode coachA = createCoach(owner, "Alice", "Running", "Berlin");
        createCoach(owner, "Bob", "Yoga", "Munich");
        long runningSportId = coachA.get("sportTypes").get(0).get("sportTypeId").asLong();

        // Full list — both coaches present, still CoachProfileResponse (has sportTypes), no AI involved.
        mockMvc.perform(get("/api/coaches").header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Alice')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Bob')]").exists());

        // Classic sport-type filter — only the Running coach.
        mockMvc.perform(get("/api/coaches/recommend")
                        .header("Authorization", owner.bearer())
                        .param("sportTypeId", String.valueOf(runningSportId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Alice')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Bob')]").doesNotExist());

        // Geolocation endpoint still responds normally (an array; empty here since geocoding is
        // disabled in tests, but the point is it works and is independent of the AI change).
        mockMvc.perform(get("/api/coaches/nearby")
                        .header("Authorization", owner.bearer())
                        .param("lat", "52.52").param("lng", "13.405").param("radiusKm", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- 1) existing community endpoints unaffected ---------------------------------------------

    @Test
    void existingCommunityEndpoints_stillWork() throws Exception {
        Account owner = register("reco-group-owner@fitconnect.test");
        long g1 = createGroup(owner, "Berlin Runners", "Running");
        createGroup(owner, "Yoga Friends", "Yoga");

        mockMvc.perform(get("/api/community-groups").header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.communityId == " + g1 + ")]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Yoga Friends')]").exists());

        mockMvc.perform(get("/api/community-groups/nearby")
                        .header("Authorization", owner.bearer())
                        .param("lat", "52.52").param("lng", "13.405").param("radiusKm", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- 2) new recommendation endpoints are guarded (no Gemini reached) ------------------------

    @Test
    void recommendedEndpoints_requireAuthentication() throws Exception {
        // No token → 401, before any controller/service logic.
        mockMvc.perform(get("/api/users/1/coaches/recommended"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/1/community-groups/recommended"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recommendedEndpoints_forbidCrossUserAccess() throws Exception {
        Account a = register("reco-self-a@fitconnect.test");
        Account b = register("reco-self-b@fitconnect.test");

        // A's token on B's userId → 403 (CurrentUser.requireSelf), no Gemini call.
        mockMvc.perform(get("/api/users/" + b.userId() + "/coaches/recommended")
                        .header("Authorization", a.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/" + b.userId() + "/community-groups/recommended")
                        .header("Authorization", a.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void recommendedEndpoints_withoutOnboarding_return400NotServerError() throws Exception {
        Account user = register("reco-no-onboarding@fitconnect.test");

        // Self, valid token, but no UserProfile yet → clean 400 (same guard as plan generation),
        // reached before any Gemini call.
        mockMvc.perform(get("/api/users/" + user.userId() + "/coaches/recommended")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("onboarding")));
        mockMvc.perform(get("/api/users/" + user.userId() + "/community-groups/recommended")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isBadRequest());
    }
}
