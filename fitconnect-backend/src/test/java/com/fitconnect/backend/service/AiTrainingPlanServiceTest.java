package com.fitconnect.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitconnect.backend.config.GeminiProperties;
import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.domain.TrainingPlan;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.domain.UserProfile;
import com.fitconnect.backend.dto.GenerateTrainingPlanRequest;
import com.fitconnect.backend.exception.BadRequestException;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiTrainingPlanService}. The Gemini HTTP call is fully mocked at the
 * {@link WebClient} fluent-chain level — there is NO real network call. This lets us exercise
 * the real prompt-building, the double JSON parse (Gemini envelope → embedded plan JSON) and
 * the entity assembly, while still covering the guarded error branches (missing profile,
 * missing API key).
 */
@ExtendWith(MockitoExtension.class)
class AiTrainingPlanServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private com.fitconnect.backend.repository.ProgressRecordRepository progressRecordRepository;
    @Mock
    private SportTypeService sportTypeService;
    @Mock
    private TrainingPlanService trainingPlanService;

    // WebClient fluent chain — mocked step by step so no HTTP request ever leaves the JVM.
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
    private AiTrainingPlanService aiTrainingPlanService;

    private static final User USER = User.builder().userId(1L).email("john@fitconnect.test").role("USER").build();
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

        aiTrainingPlanService = new AiTrainingPlanService(
                geminiProperties, userService, userProfileRepository, progressRecordRepository,
                sportTypeService, trainingPlanService, new ObjectMapper(), webClientBuilder);
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
        // The three onStatus(...) calls are chained and each returns the same ResponseSpec.
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(rawEnvelopeJson));
    }

    /** A valid Gemini envelope whose inner text is itself the plan JSON (the double-encoding). */
    private String geminiEnvelope(String planJson) {
        // planJson is embedded as a JSON string, so its quotes must be escaped.
        String escaped = planJson.replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escaped + "\"}]}}]}";
    }

    @Test
    void generatePlan_happyPathAssemblesAndPersistsPlan() {
        when(userService.getById(1L)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(PROFILE));
        SportType running = SportType.builder().sportTypeId(2L).name("Running").build();
        when(sportTypeService.getOrCreate("Running")).thenReturn(running);
        when(trainingPlanService.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        String planJson = "{\"title\":\"Beginner Runner\",\"description\":\"A gentle 6-week plan.\","
                + "\"exercises\":[{\"name\":\"Interval Run\",\"sets\":4,\"repetitions\":2},"
                + "{\"name\":\"Plank\",\"sets\":3,\"repetitions\":30}]}";
        stubGeminiReturns(geminiEnvelope(planJson));

        GenerateTrainingPlanRequest request = new GenerateTrainingPlanRequest();
        request.setSportTypeName("Running");
        request.setDurationWeeks(6);

        TrainingPlan plan = aiTrainingPlanService.generatePlan(1L, request);

        ArgumentCaptor<TrainingPlan> captor = ArgumentCaptor.forClass(TrainingPlan.class);
        verify(trainingPlanService).save(captor.capture());
        TrainingPlan persisted = captor.getValue();
        assertThat(persisted.getTitle()).isEqualTo("Beginner Runner");
        assertThat(persisted.getDuration()).isEqualTo(6);
        assertThat(persisted.getUser()).isSameAs(USER);
        assertThat(persisted.getSportType()).isSameAs(running);
        assertThat(persisted.getExercises()).hasSize(2);
        assertThat(persisted.getExercises()).extracting("name").containsExactly("Interval Run", "Plank");
        // Each exercise points back to its plan and shares the resolved sport type.
        assertThat(persisted.getExercises().get(0).getTrainingPlan()).isSameAs(persisted);
        assertThat(persisted.getExercises().get(0).getSportType()).isSameAs(running);
        assertThat(plan).isSameAs(persisted);
    }

    @Test
    void generatePlan_appliesDefaultsWhenRequestIsNull() {
        when(userService.getById(1L)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(PROFILE));
        when(sportTypeService.getOrCreate("General fitness")).thenReturn(
                SportType.builder().sportTypeId(9L).name("General fitness").build());
        when(trainingPlanService.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        String planJson = "{\"title\":\"Base Plan\",\"description\":\"Default.\",\"exercises\":[]}";
        stubGeminiReturns(geminiEnvelope(planJson));

        TrainingPlan plan = aiTrainingPlanService.generatePlan(1L, null);

        // Defaults documented in AiTrainingPlanService: "General fitness" + 8 weeks.
        assertThat(plan.getDuration()).isEqualTo(8);
        verify(sportTypeService).getOrCreate("General fitness");
    }

    @Test
    void generatePlan_throwsWhenProfileMissing() {
        when(userService.getById(1L)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiTrainingPlanService.generatePlan(1L, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("onboarding");

        // No Gemini call and nothing persisted when onboarding is missing.
        verify(webClientBuilder, never()).build();
        verify(trainingPlanService, never()).save(any());
    }

    @Test
    void generatePlan_throwsWhenApiKeyMissing() {
        geminiProperties.setApiKey("   ");
        when(userService.getById(1L)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(PROFILE));

        assertThatThrownBy(() -> aiTrainingPlanService.generatePlan(1L, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GEMINI_API_KEY");

        verify(trainingPlanService, never()).save(any());
    }

    @Test
    void generatePlan_throwsWhenGeminiReturnsNoCandidate() {
        when(userService.getById(1L)).thenReturn(USER);
        when(userProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(PROFILE));
        lenient().when(sportTypeService.getOrCreate(anyString()))
                .thenReturn(SportType.builder().sportTypeId(1L).name("General fitness").build());
        stubGeminiReturns("{\"candidates\":[]}");

        assertThatThrownBy(() -> aiTrainingPlanService.generatePlan(1L, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no content");

        verify(trainingPlanService, never()).save(any());
    }

    // --- adaptPlan -------------------------------------------------------------------------

    private TrainingPlan existingPlanOwnedBy(User owner) {
        SportType running = SportType.builder().sportTypeId(2L).name("Running").build();
        TrainingPlan plan = TrainingPlan.builder()
                .planId(50L).title("Old Plan").description("Old description").duration(8)
                .user(owner).sportType(running)
                .build();
        // A mutable exercise list so adaptPlan can clear it in place (orphanRemoval in production).
        plan.setExercises(new java.util.ArrayList<>(List.of(
                com.fitconnect.backend.domain.Exercise.builder()
                        .exerciseId(1L).name("Old Squat").sets(3).repetitions(8)
                        .trainingPlan(plan).sportType(running).build())));
        return plan;
    }

    private com.fitconnect.backend.domain.ProgressRecord progress(int completed, String notes) {
        return com.fitconnect.backend.domain.ProgressRecord.builder()
                .date(java.time.LocalDate.of(2026, 7, 20))
                .completedWorkouts(completed).notes(notes)
                .build();
    }

    @Test
    void adaptPlan_replacesExercisesInPlaceKeepingSamePlanId() {
        TrainingPlan plan = existingPlanOwnedBy(USER);
        when(userService.getById(1L)).thenReturn(USER);
        when(trainingPlanService.getById(50L)).thenReturn(plan);
        when(userProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(PROFILE));
        when(progressRecordRepository.findTop10ByUser_UserIdOrderByDateDesc(1L))
                .thenReturn(List.of(progress(3, "Feeling strong"), progress(3, "Good")));
        when(trainingPlanService.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        String planJson = "{\"title\":\"Adapted Plan\",\"description\":\"Increased intensity as you kept the pace.\","
                + "\"exercises\":[{\"name\":\"Harder Squat\",\"sets\":4,\"repetitions\":10},"
                + "{\"name\":\"Lunge\",\"sets\":3,\"repetitions\":12}]}";
        stubGeminiReturns(geminiEnvelope(planJson));

        TrainingPlan adapted = aiTrainingPlanService.adaptPlan(1L, 50L);

        // Same entity/planId, exercises swapped out, title/description refreshed.
        assertThat(adapted.getPlanId()).isEqualTo(50L);
        assertThat(adapted.getTitle()).isEqualTo("Adapted Plan");
        assertThat(adapted.getDescription()).contains("Increased intensity");
        assertThat(adapted.getExercises()).extracting("name").containsExactly("Harder Squat", "Lunge");
        assertThat(adapted.getExercises().get(0).getTrainingPlan()).isSameAs(adapted);
        verify(trainingPlanService).save(adapted);
    }

    @Test
    void adaptPlan_throwsWhenNoProgressHistory() {
        TrainingPlan plan = existingPlanOwnedBy(USER);
        when(userService.getById(1L)).thenReturn(USER);
        when(trainingPlanService.getById(50L)).thenReturn(plan);
        when(userProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(PROFILE));
        when(progressRecordRepository.findTop10ByUser_UserIdOrderByDateDesc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> aiTrainingPlanService.adaptPlan(1L, 50L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("progress history");

        verify(webClientBuilder, never()).build();
        verify(trainingPlanService, never()).save(any());
    }

    @Test
    void adaptPlan_rejectsPlanOwnedByAnotherUser() {
        User otherOwner = User.builder().userId(99L).email("someone@fitconnect.test").role("USER").build();
        TrainingPlan foreignPlan = existingPlanOwnedBy(otherOwner);
        when(userService.getById(1L)).thenReturn(USER);
        when(trainingPlanService.getById(50L)).thenReturn(foreignPlan);

        assertThatThrownBy(() -> aiTrainingPlanService.adaptPlan(1L, 50L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found for this user");

        // Bails out before profile lookup, progress lookup or any Gemini call.
        verify(userProfileRepository, never()).findByUser_UserId(any());
        verify(trainingPlanService, never()).save(any());
    }
}