package com.fitconnect.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitconnect.backend.config.GeminiProperties;
import com.fitconnect.backend.domain.CoachProfile;
import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.domain.UserProfile;
import com.fitconnect.backend.dto.CoachProfileResponse;
import com.fitconnect.backend.dto.CoachRecommendationResponse;
import com.fitconnect.backend.dto.CommunityGroupRecommendationResponse;
import com.fitconnect.backend.dto.CommunityGroupResponse;
import com.fitconnect.backend.exception.BadRequestException;
import com.fitconnect.backend.repository.CoachProfileRepository;
import com.fitconnect.backend.repository.CommunityGroupRepository;
import com.fitconnect.backend.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Personalized recommendations via the Gemini API, built ON TOP OF the existing (non-AI) coach and
 * community endpoints — it does not replace them. Given a user's {@link UserProfile} (fitnessGoal,
 * fitnessLevel, trainingFrequency), it asks Gemini to rank the most relevant coaches / community
 * groups and returns them with a short English justification each.
 *
 * <p><b>Gemini plumbing</b>: intentionally mirrors {@code AiTrainingPlanService}'s approach (same
 * {@code WebClient} usage, same strict-JSON {@code responseMimeType}, and the SAME error table —
 * missing key, 401/403, 429, 404, timeout, no-candidate/malformed → always {@link BadRequestException},
 * never a raw 500). It is deliberately self-contained (the plumbing is duplicated rather than extracted
 * from the working {@code AiTrainingPlanService}) so this feature adds zero risk to plan generation.
 *
 * <p><b>Short-circuits without calling Gemini</b>: no {@code UserProfile} → clean 400 asking to do
 * onboarding first (same guard as generation); empty coach/group list → empty result (no wasted call).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final GeminiProperties geminiProperties;
    private final UserService userService;
    private final UserProfileRepository userProfileRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final CommunityGroupRepository communityGroupRepository;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Transactional(readOnly = true)
    public List<CoachRecommendationResponse> recommendCoaches(Long userId) {
        userService.getById(userId); // 404 if the user does not exist (parity with generation)
        UserProfile profile = requireProfile(userId);

        // Same JOIN FETCH read as CoachRecommendationService.getAllCoaches — no divergence in how
        // coaches are loaded, so the existing list endpoint's behavior is mirrored, not altered.
        List<CoachProfile> coaches = coachProfileRepository.findAllWithSportTypes();
        if (coaches.isEmpty()) {
            return List.of(); // nothing to rank → don't call Gemini
        }

        CoachRecoResponse reco = callGemini(buildCoachPrompt(profile, coaches), CoachRecoResponse.class);
        return mapCoachRecommendations(reco, coaches);
    }

    @Transactional(readOnly = true)
    public List<CommunityGroupRecommendationResponse> recommendGroups(Long userId) {
        userService.getById(userId);
        UserProfile profile = requireProfile(userId);

        // Same JOIN FETCH read as CommunityService.getAllGroups (members fetched) so the mapped
        // CommunityGroupResponse (memberCount / isJoined) is identical to the non-AI list endpoint.
        List<CommunityGroup> groups = communityGroupRepository.findAllWithMembers();
        if (groups.isEmpty()) {
            return List.of();
        }

        GroupRecoResponse reco = callGemini(buildGroupPrompt(profile, groups), GroupRecoResponse.class);
        return mapGroupRecommendations(reco, groups, userId);
    }

    private UserProfile requireProfile(Long userId) {
        return userProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BadRequestException(
                        "No profile found for this user. Please complete onboarding before requesting recommendations."));
    }

    // --- Mapping (preserve Gemini's ranking order, ignore unknown/duplicate ids) -----------------

    private List<CoachRecommendationResponse> mapCoachRecommendations(
            CoachRecoResponse reco, List<CoachProfile> coaches) {
        if (reco == null || reco.recommendations() == null) {
            return List.of();
        }
        Map<Long, CoachProfile> byId = coaches.stream()
                .collect(Collectors.toMap(CoachProfile::getCoachId, c -> c, (a, b) -> a));

        List<CoachRecommendationResponse> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (CoachReco item : reco.recommendations()) {
            if (item == null || item.coachId() == null) {
                continue;
            }
            CoachProfile coach = byId.get(item.coachId());
            // Skip ids Gemini may have invented, and any duplicate it may have repeated.
            if (coach == null || !seen.add(item.coachId())) {
                continue;
            }
            result.add(new CoachRecommendationResponse(CoachProfileResponse.from(coach), item.reason()));
        }
        return result;
    }

    private List<CommunityGroupRecommendationResponse> mapGroupRecommendations(
            GroupRecoResponse reco, List<CommunityGroup> groups, Long userId) {
        if (reco == null || reco.recommendations() == null) {
            return List.of();
        }
        Map<Long, CommunityGroup> byId = groups.stream()
                .collect(Collectors.toMap(CommunityGroup::getCommunityId, g -> g, (a, b) -> a));

        List<CommunityGroupRecommendationResponse> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (GroupReco item : reco.recommendations()) {
            if (item == null || item.communityId() == null) {
                continue;
            }
            CommunityGroup group = byId.get(item.communityId());
            if (group == null || !seen.add(item.communityId())) {
                continue;
            }
            result.add(new CommunityGroupRecommendationResponse(
                    CommunityGroupResponse.from(group, userId), item.reason()));
        }
        return result;
    }

    // --- Prompts ---------------------------------------------------------------------------------

    private String buildCoachPrompt(UserProfile profile, List<CoachProfile> coaches) {
        StringBuilder list = new StringBuilder();
        for (CoachProfile c : coaches) {
            String sports = c.getSportTypes().stream()
                    .map(SportType::getName)
                    .collect(Collectors.joining(", "));
            list.append("  - coachId ").append(c.getCoachId())
                    .append(": ").append(c.getName())
                    .append(" | specialization: ").append(nullSafe(c.getSpecialization()))
                    .append(" | sports: ").append(sports.isBlank() ? "n/a" : sports)
                    .append("\n");
        }

        return """
                You are an expert fitness advisor. Recommend the most relevant coaches for a user
                based on their profile.

                User profile:
                - Fitness goal: %s
                - Level: %s
                - Desired training frequency: %s times per week

                Available coaches:
                %s
                Rank the 3 to 5 MOST relevant coaches for this user (fewer if fewer are available).
                Use ONLY coachId values taken from the list above. For each, give a short one-sentence
                justification tied to the user's goal and level.

                Respond ONLY with a valid JSON object matching exactly this schema, with no text before or after:
                {
                  "recommendations": [
                    { "coachId": number, "reason": "one short sentence" }
                  ]
                }

                All text (the reason) MUST be written in English.
                """.formatted(
                nullSafe(profile.getFitnessGoal()),
                nullSafe(profile.getFitnessLevel()),
                profile.getTrainingFrequency(),
                list.toString());
    }

    private String buildGroupPrompt(UserProfile profile, List<CommunityGroup> groups) {
        StringBuilder list = new StringBuilder();
        for (CommunityGroup g : groups) {
            String sport = g.getSportType() != null ? g.getSportType().getName() : "n/a";
            list.append("  - communityId ").append(g.getCommunityId())
                    .append(": ").append(g.getName())
                    .append(" | sport: ").append(sport)
                    .append(" | description: ").append(nullSafe(g.getDescription()))
                    .append("\n");
        }

        return """
                You are an expert fitness advisor. Recommend the most relevant community groups for a
                user based on their profile.

                User profile:
                - Fitness goal: %s
                - Level: %s
                - Desired training frequency: %s times per week

                Available community groups:
                %s
                Rank the 3 to 5 MOST relevant groups for this user (fewer if fewer are available).
                Use ONLY communityId values taken from the list above. For each, give a short one-sentence
                justification tied to the user's goal and level.

                Respond ONLY with a valid JSON object matching exactly this schema, with no text before or after:
                {
                  "recommendations": [
                    { "communityId": number, "reason": "one short sentence" }
                  ]
                }

                All text (the reason) MUST be written in English.
                """.formatted(
                nullSafe(profile.getFitnessGoal()),
                nullSafe(profile.getFitnessLevel()),
                profile.getTrainingFrequency(),
                list.toString());
    }

    private String nullSafe(String value) {
        return (value == null || value.isBlank()) ? "n/a" : value;
    }

    // --- Gemini call (same error table as AiTrainingPlanService, never a raw 500) -----------------

    private <T> T callGemini(String prompt, Class<T> responseType) {
        String apiKey = geminiProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException(
                    "Gemini API key is missing. Make sure the GEMINI_API_KEY environment variable is set.");
        }

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        String rawResponse;
        try {
            rawResponse = webClientBuilder.baseUrl(geminiProperties.getBaseUrl()).build()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(geminiProperties.getModel()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.value() == 429,
                            resp -> resp.releaseBody().then(Mono.error(new BadRequestException(
                                    "The Gemini API free quota has been reached (error 429). Please try again in a few minutes."))))
                    .onStatus(status -> status.value() == 401 || status.value() == 403,
                            resp -> resp.releaseBody().then(Mono.error(new BadRequestException(
                                    "Invalid or unauthorized Gemini API key. Check the GEMINI_API_KEY environment variable."))))
                    .onStatus(status -> status.value() == 404,
                            resp -> resp.releaseBody().then(Mono.error(new BadRequestException(
                                    "The configured Gemini model ('" + geminiProperties.getModel() + "') was not found or is no longer available on this account. "
                                            + "It is a preview model: check its availability in Google AI Studio, or update gemini.model in application.yml."))))
                    .onStatus(HttpStatusCode::isError,
                            resp -> resp.bodyToMono(String.class).defaultIfEmpty("").flatMap(body -> Mono.error(new BadRequestException(
                                    "Gemini API error (" + resp.statusCode() + "): " + body))))
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof TimeoutException || e.getCause() instanceof TimeoutException) {
                throw new BadRequestException("The Gemini API call timed out. Please try again.");
            }
            log.error("Error while calling the Gemini API", e);
            throw new BadRequestException("Unable to reach the AI recommendation service (Gemini): " + e.getMessage());
        }

        return parseRecommendations(rawResponse, responseType);
    }

    private <T> T parseRecommendations(String rawResponse, Class<T> responseType) {
        try {
            GeminiResponse response = objectMapper.readValue(rawResponse, GeminiResponse.class);
            if (response.candidates() == null || response.candidates().isEmpty()) {
                throw new BadRequestException(
                        "Gemini returned no content (response likely filtered). Please try again.");
            }
            String text = response.candidates().get(0).content().parts().get(0).text();
            return objectMapper.readValue(text, responseType);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(
                    "Gemini's response is not valid JSON, unable to build recommendations. Please try again.");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException(
                    "Unexpected response from Gemini, unable to build recommendations: " + e.getMessage());
        }
    }

    // Gemini envelope (candidates[0].content.parts[0].text holds the recommendation JSON).
    private record GeminiResponse(List<Candidate> candidates) {}
    private record Candidate(Content content) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}

    // Parsed recommendation payloads.
    private record CoachRecoResponse(List<CoachReco> recommendations) {}
    private record CoachReco(Long coachId, String reason) {}
    private record GroupRecoResponse(List<GroupReco> recommendations) {}
    private record GroupReco(Long communityId, String reason) {}
}
