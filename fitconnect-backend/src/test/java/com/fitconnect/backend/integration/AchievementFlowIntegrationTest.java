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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test of the achievement system over real HTTP (MockMvc) on H2, exercising the
 * automatic awarding that fires from logging progress. Relies on the starter badges inserted by
 * {@code AchievementSeeder} at context startup (First Workout=1, Consistency=10, Dedicated=50
 * cumulative completed workouts).
 *
 * <p>The central assertion (as requested): posting {@code ProgressRecord}s awards an achievement
 * exactly when the cumulative total crosses its threshold — not before.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AchievementFlowIntegrationTest {

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

    private void logWorkouts(Account a, int completed, String date) throws Exception {
        mockMvc.perform(post("/api/users/" + a.userId() + "/progress")
                        .header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"completedWorkouts\":" + completed + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void catalogue_isSeededAndRequiresAuthentication() throws Exception {
        Account a = register("ach-cat@fitconnect.test");

        mockMvc.perform(get("/api/achievements"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/achievements").header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name",
                        hasItem("First Workout")))
                .andExpect(jsonPath("$[*].name",
                        containsInAnyOrder("First Workout", "Consistency", "Dedicated")));
    }

    @Test
    void loggingProgress_awardsAchievementsAtTheRightThreshold() throws Exception {
        Account a = register("ach-trigger@fitconnect.test");

        // No workout logged yet → no badge.
        mockMvc.perform(get("/api/users/" + a.userId() + "/achievements").header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 1 workout total → "First Workout" (threshold 1) earned, "Consistency" (10) not yet.
        logWorkouts(a, 1, "2026-07-20");
        mockMvc.perform(get("/api/users/" + a.userId() + "/achievements").header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("First Workout"))
                .andExpect(jsonPath("$[0].earnedAt").isNotEmpty());

        // 9 more → 10 total → "Consistency" now earned too, "Dedicated" (50) still not.
        logWorkouts(a, 9, "2026-07-21");
        mockMvc.perform(get("/api/users/" + a.userId() + "/achievements").header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("First Workout", "Consistency")));
    }

    @Test
    void earnedAchievements_rejectCrossUserAccess() throws Exception {
        Account alice = register("ach-alice@fitconnect.test");
        Account bob = register("ach-bob@fitconnect.test");

        // Alice's token, Bob's id → 403 (JWT + self, same guard as the other /users/{id}/... routes).
        mockMvc.perform(get("/api/users/" + bob.userId() + "/achievements").header("Authorization", alice.bearer()))
                .andExpect(status().isForbidden());
    }
}
