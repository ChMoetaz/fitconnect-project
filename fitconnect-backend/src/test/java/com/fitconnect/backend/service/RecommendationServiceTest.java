package com.fitconnect.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitconnect.backend.config.GeminiProperties;
import com.fitconnect.backend.domain.CoachProfile;
import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.domain.UserProfile;
import com.fitconnect.backend.dto.CoachRecommendationResponse;
import com.fitconnect.backend.dto.CommunityGroupRecommendationResponse;
import com.fitconnect.backend.exception.BadRequestException;
import com.fitconnect.backend.repository.CoachProfileRepository;
import com.fitconnect.backend.repository.CommunityGroupRepository;
import com.fitconnect.backend.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecommendationService}. The Gemini HTTP call is fully mocked at the
 * {@link WebClient} fluent-chain level (same technique as {@code AiTrainingPlanServiceTest}) — no real
 * network call. Covers the happy path (ranking order preserved, reason attached, unknown/duplicate ids
 * ignored), the empty-list short-circuit (no Gemini call), and the guarded error branches (missing
 * profile, missing API key, no candidate) — all surfaced as {@link BadRequestException}, never a 500.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private CoachProfileRepository coachProfileRepository;
    @Mock
    private CommunityGroupRepository communityGroupRepository;

    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    private GeminiProperties geminiProperties;
    private RecommendationService recommendationService;

    private static final Long USER_ID = 1L;
    private static final User USER = User.builder().userId(USER_ID).email("john@fitconnect.test").role("USER").build();
    private static final UserProfile PROFILE = UserProfile.builder()
            .profileId(1L).user(USER)
            .fitnessGoal("MUSCLE_GAIN").fitnessLevel("BEGINNER").trainingFrequency(3)
            .build();

    @BeforeEach
    void setUp() {
        geminiProperties = new GeminiProperties();
        geminiProperties.setApiKey("test-gemini-key");
        geminiProperties.setModel("gemini-test-model");
        geminiProperties.setBaseUrl("http://localhost:0");

        recommendationService = new RecommendationService(
                geminiProperties, userService, userProfileRepository, coachProfileRepository,
                communityGroupRepository, new ObjectMapper(), webClientBuilder);
    }

    @SuppressWarnings("unchecked")
    private void stubGeminiReturns(String rawEnvelopeJson) {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(rawEnvelopeJson));
    }

    private String geminiEnvelope(String innerJson) {
        String escaped = innerJson.replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escaped + "\"}]}}]}";
    }

    private CoachProfile coach(long id, String name) {
        return CoachProfile.builder()
                .coachId(id).name(name).specialization("Strength")
                .sportTypes(Set.of(SportType.builder().sportTypeId(id).name("Running").build()))
                .build();
    }

    private CommunityGroup group(long id, String name) {
        return CommunityGroup.builder()
                .communityId(id).name(name).description("A group")
                .sportType(SportType.builder().sportTypeId(id).name("Running").build())
                .build();
    }

    // --- coaches --------------------------------------------------------------------------------

    @Test
    void recommendCoaches_happyPath_preservesRankingAndAttachesReason() {
        when(userService.getById(USER_ID)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(PROFILE));
        when(coachProfileRepository.findAllWithSportTypes())
                .thenReturn(List.of(coach(10L, "Alice"), coach(20L, "Bob"), coach(30L, "Cara")));

        // Gemini ranks 20 then 10 (30 not recommended).
        String inner = "{\"recommendations\":[{\"coachId\":20,\"reason\":\"Great for muscle gain.\"},"
                + "{\"coachId\":10,\"reason\":\"Beginner-friendly.\"}]}";
        stubGeminiReturns(geminiEnvelope(inner));

        List<CoachRecommendationResponse> result = recommendationService.recommendCoaches(USER_ID);

        assertThat(result).extracting(r -> r.getCoach().getCoachId())
                .containsExactly(20L, 10L); // Gemini's order preserved
        assertThat(result.get(0).getCoach().getName()).isEqualTo("Bob");
        assertThat(result.get(0).getReason()).isEqualTo("Great for muscle gain.");
        assertThat(result.get(1).getReason()).isEqualTo("Beginner-friendly.");
    }

    @Test
    void recommendCoaches_ignoresUnknownAndDuplicateIds() {
        when(userService.getById(USER_ID)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(PROFILE));
        when(coachProfileRepository.findAllWithSportTypes())
                .thenReturn(List.of(coach(10L, "Alice"), coach(20L, "Bob")));

        // 999 does not exist; 20 is repeated → only 20 (once) then 10 survive.
        String inner = "{\"recommendations\":[{\"coachId\":20,\"reason\":\"r1\"},"
                + "{\"coachId\":999,\"reason\":\"invented\"},"
                + "{\"coachId\":20,\"reason\":\"dup\"},"
                + "{\"coachId\":10,\"reason\":\"r2\"}]}";
        stubGeminiReturns(geminiEnvelope(inner));

        List<CoachRecommendationResponse> result = recommendationService.recommendCoaches(USER_ID);

        assertThat(result).extracting(r -> r.getCoach().getCoachId())
                .containsExactly(20L, 10L);
    }

    @Test
    void recommendCoaches_emptyList_returnsEmptyWithoutCallingGemini() {
        when(userService.getById(USER_ID)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(PROFILE));
        when(coachProfileRepository.findAllWithSportTypes()).thenReturn(List.of());

        List<CoachRecommendationResponse> result = recommendationService.recommendCoaches(USER_ID);

        assertThat(result).isEmpty();
        verify(webClientBuilder, never()).build(); // no wasted Gemini call
    }

    @Test
    void recommendCoaches_missingProfile_throwsBadRequest() {
        when(userService.getById(USER_ID)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recommendationService.recommendCoaches(USER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("onboarding");

        verify(webClientBuilder, never()).build();
        verify(coachProfileRepository, never()).findAllWithSportTypes();
    }

    @Test
    void recommendCoaches_missingApiKey_throwsBadRequest() {
        geminiProperties.setApiKey("   ");
        when(userService.getById(USER_ID)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(PROFILE));
        when(coachProfileRepository.findAllWithSportTypes()).thenReturn(List.of(coach(10L, "Alice")));

        assertThatThrownBy(() -> recommendationService.recommendCoaches(USER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void recommendCoaches_noCandidate_throwsBadRequest() {
        when(userService.getById(USER_ID)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(PROFILE));
        when(coachProfileRepository.findAllWithSportTypes()).thenReturn(List.of(coach(10L, "Alice")));
        stubGeminiReturns("{\"candidates\":[]}");

        assertThatThrownBy(() -> recommendationService.recommendCoaches(USER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no content");
    }

    // --- community groups -----------------------------------------------------------------------

    @Test
    void recommendGroups_happyPath_preservesRankingAndAttachesReason() {
        when(userService.getById(USER_ID)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(PROFILE));
        when(communityGroupRepository.findAllWithMembers())
                .thenReturn(List.of(group(100L, "Berlin Runners"), group(200L, "Yoga Friends")));

        String inner = "{\"recommendations\":[{\"communityId\":200,\"reason\":\"Matches your goal.\"},"
                + "{\"communityId\":100,\"reason\":\"Beginner runners welcome.\"}]}";
        stubGeminiReturns(geminiEnvelope(inner));

        List<CommunityGroupRecommendationResponse> result = recommendationService.recommendGroups(USER_ID);

        assertThat(result).extracting(r -> r.getGroup().getCommunityId())
                .containsExactly(200L, 100L);
        assertThat(result.get(0).getGroup().getName()).isEqualTo("Yoga Friends");
        assertThat(result.get(0).getReason()).isEqualTo("Matches your goal.");
    }

    @Test
    void recommendGroups_emptyList_returnsEmptyWithoutCallingGemini() {
        when(userService.getById(USER_ID)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(PROFILE));
        when(communityGroupRepository.findAllWithMembers()).thenReturn(List.of());

        assertThat(recommendationService.recommendGroups(USER_ID)).isEmpty();
        verify(webClientBuilder, never()).build();
    }
}
