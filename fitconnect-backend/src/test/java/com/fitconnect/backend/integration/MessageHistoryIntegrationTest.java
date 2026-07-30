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
 * Integration test of the membership guard on the chat history endpoint
 * ({@code GET /api/community-groups/{groupId}/messages}) over real HTTP (MockMvc) on H2.
 *
 * <p>The endpoint is not merely "JWT required": the authenticated caller must also be a MEMBER of the
 * group, mirroring the send side (a non-member cannot post). Asserts the four outcomes: a member gets
 * 200, an authenticated non-member gets a clean 403 (not a 500), no token gets 401, and a missing
 * group gets 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MessageHistoryIntegrationTest {

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
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("communityId").asLong();
    }

    private void join(Account a, long groupId) throws Exception {
        mockMvc.perform(post("/api/community-groups/" + groupId + "/join")
                        .header("Authorization", a.bearer())
                        .param("userId", String.valueOf(a.userId())))
                .andExpect(status().isOk());
    }

    @Test
    void member_canReadHistory_returns200() throws Exception {
        Account member = register("chat-member@fitconnect.test");
        long groupId = createGroup(member, "Berlin Runners");
        join(member, groupId);

        // No message posted yet (posting goes through WebSocket) → an empty history, but 200 + a member.
        mockMvc.perform(get("/api/community-groups/" + groupId + "/messages")
                        .header("Authorization", member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void nonMember_isForbidden_returns403() throws Exception {
        Account owner = register("chat-owner@fitconnect.test");
        Account stranger = register("chat-stranger@fitconnect.test");
        long groupId = createGroup(owner, "Private Runners");
        join(owner, groupId); // owner is a member; stranger deliberately never joins

        // Authenticated (valid token) but NOT a member → clean 403, not a 500.
        mockMvc.perform(get("/api/community-groups/" + groupId + "/messages")
                        .header("Authorization", stranger.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_isUnauthorized_returns401() throws Exception {
        Account owner = register("chat-noauth@fitconnect.test");
        long groupId = createGroup(owner, "Anon Runners");

        mockMvc.perform(get("/api/community-groups/" + groupId + "/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingGroup_returns404() throws Exception {
        Account member = register("chat-404@fitconnect.test");

        mockMvc.perform(get("/api/community-groups/999999/messages")
                        .header("Authorization", member.bearer()))
                .andExpect(status().isNotFound());
    }
}
