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
 * Integration test of the stateless-JWT authorization rules, exercised over two different
 * protected route families ({@code /api/users/{id}} and {@code /api/users/{id}/progress}):
 *
 * <ul>
 *   <li>no token → 401 (rejected by the filter chain via RestAuthenticationEntryPoint)</li>
 *   <li>valid token but another user's id in the path → 403 (CurrentUser.requireSelf)</li>
 *   <li>valid token on one's own resources → 200</li>
 * </ul>
 *
 * Two real users are registered to obtain two genuinely different tokens, so the cross-access
 * 403 is proven against real ownership, not a hand-crafted token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private record Account(long userId, String token) {}

    private Account register(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"secret123\"}";
        MvcResult result = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Account(node.get("userId").asLong(), node.get("accessToken").asText());
    }

    @Test
    void protectedRoutes_rejectMissingToken_withUnauthorized() throws Exception {
        Account alice = register("alice@fitconnect.test");

        mockMvc.perform(get("/api/users/" + alice.userId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/" + alice.userId() + "/progress"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoutes_rejectCrossUserAccess_withForbidden() throws Exception {
        Account alice = register("alice2@fitconnect.test");
        Account bob = register("bob2@fitconnect.test");

        // Alice's token, Bob's id in the path → 403 on both route families.
        mockMvc.perform(get("/api/users/" + bob.userId())
                        .header("Authorization", "Bearer " + alice.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/" + bob.userId() + "/progress")
                        .header("Authorization", "Bearer " + alice.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedRoutes_allowSelfAccess_withOk() throws Exception {
        Account alice = register("alice3@fitconnect.test");

        mockMvc.perform(get("/api/users/" + alice.userId())
                        .header("Authorization", "Bearer " + alice.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(alice.userId()))
                .andExpect(jsonPath("$.email").value("alice3@fitconnect.test"));

        // Own progress list is reachable (empty at this point, but a 200 not a 401/403).
        mockMvc.perform(get("/api/users/" + alice.userId() + "/progress")
                        .header("Authorization", "Bearer " + alice.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}