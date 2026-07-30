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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint-level test of DELETE /api/community-groups/{communityId} over real HTTP (MockMvc) on H2:
 * a member joins the group, then it is deleted → 204 and it no longer shows up in the list; a
 * second delete of the same id → 404; and the route requires a JWT (401 without one). The DB-level
 * proof that every dependent row is actually removed lives in CommunityServiceDeleteTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityGroupDeleteIntegrationTest {

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

    private long createGroup(Account a, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/community-groups")
                        .header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"sportTypeName\":\"Running\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("communityId").asLong();
    }

    @Test
    void deleteGroup_withMember_returns204_thenGone_thenSecondDelete404() throws Exception {
        Account a = register("group-delete@fitconnect.test");
        long groupId = createGroup(a, "Deletable Group");

        // Give the group a member (a real user_community_groups row) so the delete has to cascade.
        mockMvc.perform(post("/api/community-groups/" + groupId + "/join")
                        .header("Authorization", a.bearer())
                        .param("userId", String.valueOf(a.userId())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/community-groups/" + groupId).header("Authorization", a.bearer()))
                .andExpect(status().isNoContent());

        // No longer in the list.
        mockMvc.perform(get("/api/community-groups").header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.communityId == " + groupId + ")]").isEmpty());

        // Deleting the same id again → 404.
        mockMvc.perform(delete("/api/community-groups/" + groupId).header("Authorization", a.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteGroup_requiresAuthentication() throws Exception {
        Account a = register("group-delete-auth@fitconnect.test");
        long groupId = createGroup(a, "Protected Group");

        mockMvc.perform(delete("/api/community-groups/" + groupId))
                .andExpect(status().isUnauthorized());
    }
}
