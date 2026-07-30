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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the admin layer over real HTTP (MockMvc) on H2. Covers point 6:
 * <ul>
 *   <li>a non-admin gets 403 on every {@code /api/admin/...} route and on the new admin-gated coach
 *       {@code PUT}/{@code DELETE} (and the community {@code PUT});</li>
 *   <li>the seeded admin (admin@gmail.com / azerty123, created idempotently by {@code AdminSeeder} — it
 *       runs in the H2 test context too) can list / change role / delete users, and update/delete
 *       coaches and update groups, without error;</li>
 *   <li>an invalid role value is a clean 400.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminEndpointsIntegrationTest {

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

    /** Logs in as the seeded admin and returns its bearer token. */
    private String adminBearer() throws Exception {
        String body = "{\"email\":\"admin@gmail.com\",\"password\":\"azerty123\"}";
        MvcResult result = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long createCoach(String bearer, String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"specialization\":\"Strength\","
                + "\"location\":\"Berlin\",\"sportTypeNames\":[\"Weightlifting\"]}";
        MvcResult result = mockMvc.perform(post("/api/coaches")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("coachId").asLong();
    }

    private long createGroup(String bearer, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/community-groups")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"sportTypeName\":\"Running\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("communityId").asLong();
    }

    // --- non-admin is forbidden everywhere -------------------------------------------------------

    @Test
    void nonAdmin_isForbiddenOnAllAdminUserRoutes() throws Exception {
        Account user = register("admin-nonadmin-users@fitconnect.test");

        mockMvc.perform(get("/api/admin/users").header("Authorization", user.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/users/1/role")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/users/1").header("Authorization", user.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdmin_isForbiddenOnCoachAndGroupMutations() throws Exception {
        Account user = register("admin-nonadmin-mutations@fitconnect.test");

        // requireAdmin runs before anything else, so an arbitrary id still yields 403 (not 404).
        mockMvc.perform(put("/api/coaches/1")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacker\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/coaches/1").header("Authorization", user.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/community-groups/1")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoutes_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    // --- admin happy paths -----------------------------------------------------------------------

    @Test
    void admin_canListUsers_withoutPasswords() throws Exception {
        String admin = adminBearer();
        Account user = register("admin-list-target@fitconnect.test");

        mockMvc.perform(get("/api/admin/users").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'admin@gmail.com')]").exists())
                .andExpect(jsonPath("$[?(@.userId == " + user.userId() + ")]").exists())
                // UserResponse never carries a password field.
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void admin_canChangeUserRole_andRejectsInvalidValue() throws Exception {
        String admin = adminBearer();
        Account user = register("admin-role-target@fitconnect.test");

        mockMvc.perform(patch("/api/admin/users/" + user.userId() + "/role")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"COACH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.userId()))
                .andExpect(jsonPath("$.role").value("COACH"));

        // Invalid role → clean 400.
        mockMvc.perform(patch("/api/admin/users/" + user.userId() + "/role")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"WIZARD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_canDeleteUserWithDependencies() throws Exception {
        String admin = adminBearer();
        Account victim = register("admin-delete-target@fitconnect.test");

        // Give the user real dependents: a profile (onboarding) and a community membership.
        mockMvc.perform(post("/api/users/" + victim.userId() + "/onboarding")
                        .header("Authorization", victim.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fitnessGoal\":\"ENDURANCE\",\"fitnessLevel\":\"BEGINNER\","
                                + "\"trainingFrequency\":3,\"sportTypeName\":\"Running\"}"))
                .andExpect(status().isOk());
        long groupId = createGroup(admin, "Delete Test Runners");
        mockMvc.perform(post("/api/community-groups/" + groupId + "/join")
                        .header("Authorization", victim.bearer())
                        .param("userId", String.valueOf(victim.userId())))
                .andExpect(status().isOk());

        // Delete succeeds despite the dependents (no FK failure) → 204, then gone from the list.
        mockMvc.perform(delete("/api/admin/users/" + victim.userId()).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/users").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == " + victim.userId() + ")]").doesNotExist());
    }

    @Test
    void admin_canUpdateAndDeleteCoach() throws Exception {
        String admin = adminBearer();
        long coachId = createCoach(admin, "Old Name");

        mockMvc.perform(put("/api/coaches/" + coachId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\",\"specialization\":\"Powerlifting\","
                                + "\"experienceYears\":20,\"location\":\"Munich\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.specialization").value("Powerlifting"))
                .andExpect(jsonPath("$.experienceYears").value(20));

        mockMvc.perform(delete("/api/coaches/" + coachId).header("Authorization", admin))
                .andExpect(status().isNoContent());
        // Gone afterwards.
        mockMvc.perform(get("/api/coaches/" + coachId).header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_canUpdateCommunityGroup() throws Exception {
        String admin = adminBearer();
        long groupId = createGroup(admin, "Old Group Name");

        mockMvc.perform(put("/api/community-groups/" + groupId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Group Name\",\"description\":\"Updated\","
                                + "\"location\":\"Hamburg\",\"sportTypeName\":\"Cycling\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Group Name"))
                .andExpect(jsonPath("$.description").value("Updated"));
    }
}
