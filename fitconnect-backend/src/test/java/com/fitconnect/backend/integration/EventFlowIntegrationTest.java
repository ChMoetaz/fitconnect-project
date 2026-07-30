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

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test of the fitness-events flow over real HTTP (MockMvc) on H2:
 * create group → create event → list/get → register attendee → unregister → delete, plus the
 * auth guard and the nested-resource 404 (event that belongs to a different group).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventFlowIntegrationTest {

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

    private long createEvent(Account a, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/community-groups/" + groupId + "/events")
                        .header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Saturday Group Run\",\"description\":\"10k\","
                                + "\"eventDate\":\"2026-08-01T10:00:00\",\"location\":\"Tiergarten\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andExpect(jsonPath("$.attendeeCount").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("eventId").asLong();
    }

    @Test
    void fullEventLifecycle_createRegisterUnregisterDelete() throws Exception {
        Account a = register("event-owner@fitconnect.test");
        long groupId = createGroup(a, "Berlin Runners");
        long eventId = createEvent(a, groupId);

        // List and get
        mockMvc.perform(get("/api/community-groups/" + groupId + "/events").header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventId").value(eventId));
        mockMvc.perform(get("/api/community-groups/" + groupId + "/events/" + eventId).header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Saturday Group Run"));

        // Register the user as an attendee → attendeeCount 1, attendeeIds = [userId]
        mockMvc.perform(post("/api/community-groups/" + groupId + "/events/" + eventId + "/register")
                        .header("Authorization", a.bearer()).param("userId", String.valueOf(a.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendeeCount").value(1))
                .andExpect(jsonPath("$.attendeeIds", contains((int) a.userId())));

        // Unregister → back to 0
        mockMvc.perform(post("/api/community-groups/" + groupId + "/events/" + eventId + "/unregister")
                        .header("Authorization", a.bearer()).param("userId", String.valueOf(a.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendeeCount").value(0));

        // Delete → 204, then get → 404
        mockMvc.perform(delete("/api/community-groups/" + groupId + "/events/" + eventId).header("Authorization", a.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/community-groups/" + groupId + "/events/" + eventId).header("Authorization", a.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void events_requireAuthentication() throws Exception {
        Account a = register("event-auth@fitconnect.test");
        long groupId = createGroup(a, "Auth Group");

        mockMvc.perform(get("/api/community-groups/" + groupId + "/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void event_notReachableUnderAnotherGroup() throws Exception {
        Account a = register("event-nesting@fitconnect.test");
        long groupA = createGroup(a, "Group A");
        long groupB = createGroup(a, "Group B");
        long eventId = createEvent(a, groupA);

        // Event belongs to group A; requesting it under group B → 404, not the event of another group.
        mockMvc.perform(get("/api/community-groups/" + groupB + "/events/" + eventId).header("Authorization", a.bearer()))
                .andExpect(status().isNotFound());
    }
}
