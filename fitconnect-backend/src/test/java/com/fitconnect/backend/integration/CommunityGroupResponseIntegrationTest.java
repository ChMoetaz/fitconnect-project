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
 * Integration test of the CommunityGroupResponse view (GET /api/community-groups) over real HTTP
 * (MockMvc) on H2. Asserts that memberCount and isJoined are computed correctly after a real join —
 * including that isJoined is caller-specific (true for the member, false for another authenticated
 * user), that the ?sportTypeId= variant returns the same DTO, and that the raw @JsonIgnore members
 * collection never leaks into the JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityGroupResponseIntegrationTest {

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

    /** JSON path selecting the group with the given communityId inside the returned array. */
    private String group(long groupId) {
        return "$[?(@.communityId == " + groupId + ")]";
    }

    @Test
    void memberCountAndIsJoined_reflectRealMembership() throws Exception {
        Account owner = register("group-owner@fitconnect.test");
        Account other = register("group-other@fitconnect.test");
        long groupId = createGroup(owner, "Berlin Runners");

        // Before joining: memberCount 0, isJoined false, and the @JsonIgnore members collection
        // must not appear in the JSON at all.
        mockMvc.perform(get("/api/community-groups").header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(group(groupId) + ".memberCount").value(0))
                .andExpect(jsonPath(group(groupId) + ".isJoined").value(false))
                .andExpect(jsonPath(group(groupId) + ".sportTypeName").value("Running"))
                .andExpect(jsonPath(group(groupId) + ".members").doesNotExist());

        // Owner joins the group (owning side User.communityGroups is what actually persists — bug 4b).
        mockMvc.perform(post("/api/community-groups/" + groupId + "/join")
                        .header("Authorization", owner.bearer())
                        .param("userId", String.valueOf(owner.userId())))
                .andExpect(status().isOk());

        // For the owner: memberCount 1 and isJoined true.
        mockMvc.perform(get("/api/community-groups").header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(group(groupId) + ".memberCount").value(1))
                .andExpect(jsonPath(group(groupId) + ".isJoined").value(true));

        // For another authenticated user: same memberCount 1, but isJoined false (caller-specific).
        mockMvc.perform(get("/api/community-groups").header("Authorization", other.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(group(groupId) + ".memberCount").value(1))
                .andExpect(jsonPath(group(groupId) + ".isJoined").value(false));
    }

    @Test
    void sportTypeIdFilter_returnsSameDtoWithMembershipFlags() throws Exception {
        Account owner = register("group-filter@fitconnect.test");
        long groupId = createGroup(owner, "Filtered Runners");
        mockMvc.perform(post("/api/community-groups/" + groupId + "/join")
                        .header("Authorization", owner.bearer())
                        .param("userId", String.valueOf(owner.userId())))
                .andExpect(status().isOk());

        // Read back the sportTypeId the DTO exposes, then re-query through the ?sportTypeId= variant.
        MvcResult listed = mockMvc.perform(get("/api/community-groups").header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andReturn();
        JsonNode arr = objectMapper.readTree(listed.getResponse().getContentAsString());
        long sportTypeId = -1;
        for (JsonNode g : arr) {
            if (g.get("communityId").asLong() == groupId) {
                sportTypeId = g.get("sportTypeId").asLong();
            }
        }

        mockMvc.perform(get("/api/community-groups")
                        .header("Authorization", owner.bearer())
                        .param("sportTypeId", String.valueOf(sportTypeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(group(groupId) + ".memberCount").value(1))
                .andExpect(jsonPath(group(groupId) + ".isJoined").value(true));
    }

    @Test
    void list_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/community-groups"))
                .andExpect(status().isUnauthorized());
    }
}
