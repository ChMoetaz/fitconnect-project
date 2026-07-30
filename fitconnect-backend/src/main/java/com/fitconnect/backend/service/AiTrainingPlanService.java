package com.fitconnect.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitconnect.backend.config.GeminiProperties;
import com.fitconnect.backend.domain.Exercise;
import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.domain.TrainingPlan;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.domain.ProgressRecord;
import com.fitconnect.backend.domain.UserProfile;
import com.fitconnect.backend.dto.GenerateTrainingPlanRequest;
import com.fitconnect.backend.exception.BadRequestException;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.ProgressRecordRepository;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Generates training plans via the Gemini API (Google) from the user's profile, then
 * delegates persistence to {@link TrainingPlanService}. This service never touches the
 * repository directly: it only calls Gemini, parses the response and assembles the graph
 * of entities to persist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiTrainingPlanService {

    private static final int DEFAULT_DURATION_WEEKS = 8;
    // The number of recent progress records fed to Gemini is baked into the repository query name
    // (ProgressRecordRepository.findTop10ByUser_UserIdOrderByDateDesc).
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final GeminiProperties geminiProperties;
    private final UserService userService;
    private final UserProfileRepository userProfileRepository;
    private final ProgressRecordRepository progressRecordRepository;
    private final SportTypeService sportTypeService;
    private final TrainingPlanService trainingPlanService;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Transactional
    public TrainingPlan generatePlan(Long userId, GenerateTrainingPlanRequest request) {
        User user = userService.getById(userId);
        UserProfile profile = userProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BadRequestException(
                        "No profile found for this user. Please complete onboarding before generating a training plan."));

        String sportTypeName = (request != null && request.getSportTypeName() != null && !request.getSportTypeName().isBlank())
                ? request.getSportTypeName().trim()
                : "General fitness";
        int durationWeeks = (request != null && request.getDurationWeeks() != null)
                ? request.getDurationWeeks()
                : DEFAULT_DURATION_WEEKS;

        String prompt = buildPrompt(profile, sportTypeName, durationWeeks);
        GeneratedPlan generated = callGemini(prompt);
        SportType sportType = sportTypeService.getOrCreate(sportTypeName);

        TrainingPlan plan = TrainingPlan.builder()
                .title(generated.title())
                .duration(durationWeeks)
                .description(generated.description())
                .user(user)
                .sportType(sportType)
                .build();

        plan.setExercises(toExercises(generated, plan, sportType));

        return trainingPlanService.save(plan);
    }

    /**
     * Adaptive training plan: re-tunes an EXISTING plan (same planId) from the user's real
     * progress history. Rather than creating a new plan, it replaces the plan's exercises in
     * place — increasing or reducing intensity depending on whether the user is keeping up with
     * the intended rhythm — and refreshes its title/description.
     *
     * <p>Requires at least one {@link ProgressRecord}: without any logged workout there is nothing
     * to adapt from, so it fails with a clear {@link BadRequestException} inviting the user to log
     * a few sessions first.
     */
    @Transactional
    public TrainingPlan adaptPlan(Long userId, Long planId) {
        User user = userService.getById(userId);
        TrainingPlan plan = trainingPlanService.getById(planId);

        // getById() finds the plan by id alone; make sure it actually belongs to this user so one
        // cannot adapt someone else's plan by guessing an id (requireSelf only guards the path userId).
        if (plan.getUser() == null || !plan.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Training plan not found for this user: " + planId);
        }

        UserProfile profile = userProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BadRequestException(
                        "No profile found for this user. Please complete onboarding before adapting a training plan."));

        List<ProgressRecord> history = progressRecordRepository.findTop10ByUser_UserIdOrderByDateDesc(userId);
        if (history.isEmpty()) {
            throw new BadRequestException(
                    "Not enough progress history to adapt this plan. Log at least a few workout sessions "
                            + "(POST /api/users/" + userId + "/progress) before requesting an adaptation.");
        }

        String prompt = buildAdaptPrompt(profile, plan, history);
        GeneratedPlan generated = callGemini(prompt);

        // Same TrainingPlan (same planId): only its exercises are swapped out. orphanRemoval=true on
        // TrainingPlan.exercises deletes the old rows, the cascade persists the new ones. The
        // collection was eagerly fetched by getById (JOIN FETCH), so clearing it here is safe.
        SportType sportType = plan.getSportType();
        plan.getExercises().clear();
        plan.getExercises().addAll(toExercises(generated, plan, sportType));

        // Reflect the adaptation in the plan's own text so the change is visible to the user.
        plan.setTitle(generated.title());
        plan.setDescription(generated.description());

        return trainingPlanService.save(plan);
    }

    /** Maps the AI-generated exercises onto Exercise entities linked back to the given plan. */
    private List<Exercise> toExercises(GeneratedPlan generated, TrainingPlan plan, SportType sportType) {
        List<Exercise> exercises = new ArrayList<>();
        if (generated.exercises() != null) {
            for (GeneratedExercise ex : generated.exercises()) {
                exercises.add(Exercise.builder()
                        .name(ex.name())
                        .sets(ex.sets())
                        .repetitions(ex.repetitions())
                        .trainingPlan(plan)
                        .sportType(sportType)
                        .build());
            }
        }
        return exercises;
    }

    /**
     * Single entry point for the Gemini call: checks the API key, sends the prompt, maps HTTP
     * errors to clean {@link BadRequestException}s (never a raw 500) and parses the plan JSON.
     * Both {@link #generatePlan} and {@link #adaptPlan} funnel through here with their own prompt.
     */
    private GeneratedPlan callGemini(String prompt) {
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
            throw new BadRequestException("Unable to reach the AI generation service (Gemini): " + e.getMessage());
        }

        return parseGeneratedPlan(rawResponse);
    }

    private GeneratedPlan parseGeneratedPlan(String rawResponse) {
        try {
            GeminiResponse response = objectMapper.readValue(rawResponse, GeminiResponse.class);
            if (response.candidates() == null || response.candidates().isEmpty()) {
                throw new BadRequestException("Gemini returned no content (response likely filtered). Please try again.");
            }
            String text = response.candidates().get(0).content().parts().get(0).text();
            return objectMapper.readValue(text, GeneratedPlan.class);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Gemini's response is not valid JSON, unable to create the plan. Please try again.");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Unexpected response from Gemini, unable to create the plan: " + e.getMessage());
        }
    }

    private String buildPrompt(UserProfile profile, String sportTypeName, int durationWeeks) {
        return """
                You are an expert fitness coach. Generate a personalized training plan.

                User profile:
                - Fitness goal: %s
                - Level: %s
                - Desired training frequency: %s times per week
                - Target sport/activity: %s
                - Plan duration: %d weeks

                Respond ONLY with a valid JSON object matching exactly this schema, with no text before or after:
                {
                  "title": "short plan title",
                  "description": "plan description in 2 to 3 sentences, consistent with the user's level and frequency",
                  "exercises": [
                    { "name": "exercise name", "sets": number_of_sets, "repetitions": number_of_repetitions }
                  ]
                }

                All text content (title, description and exercise names) MUST be written in English.
                Propose between 4 and 8 exercises adapted to the indicated level and frequency.
                """.formatted(
                profile.getFitnessGoal(),
                profile.getFitnessLevel(),
                profile.getTrainingFrequency(),
                sportTypeName,
                durationWeeks
        );
    }

    private String buildAdaptPrompt(UserProfile profile, TrainingPlan plan, List<ProgressRecord> history) {
        StringBuilder currentExercises = new StringBuilder();
        for (Exercise ex : plan.getExercises()) {
            currentExercises.append("  - ").append(ex.getName())
                    .append(": ").append(ex.getSets()).append(" sets x ")
                    .append(ex.getRepetitions()).append(" reps\n");
        }

        StringBuilder progressLines = new StringBuilder();
        for (ProgressRecord record : history) {
            progressLines.append("  - ").append(record.getDate())
                    .append(": ").append(record.getCompletedWorkouts()).append(" workout(s) completed");
            if (record.getNotes() != null && !record.getNotes().isBlank()) {
                progressLines.append(" — notes: ").append(record.getNotes());
            }
            progressLines.append("\n");
        }

        int targetFrequency = profile.getTrainingFrequency() != null ? profile.getTrainingFrequency() : 0;

        return """
                You are an expert fitness coach. Adapt an EXISTING training plan based on the user's
                real progress, keeping the same overall goal but adjusting the intensity.

                User profile:
                - Fitness goal: %s
                - Level: %s
                - Target training frequency: %d times per week

                Current plan:
                - Title: %s
                - Description: %s
                - Exercises:
                %s
                Recent progress history (most recent first), where "workouts completed" is how many
                sessions the user actually did per logged entry versus the target of %d per week:
                %s
                Adaptation rules:
                - If the user is consistently keeping up with (or exceeding) the intended frequency,
                  INCREASE the intensity: add sets/repetitions or slightly harder exercise variations.
                - If the user is falling behind the intended frequency, REDUCE the intensity to a more
                  sustainable level so they can rebuild consistency.
                - Take the free-text notes into account (e.g. fatigue, pain, motivation) when deciding.

                Respond ONLY with a valid JSON object matching exactly this schema, with no text before or after:
                {
                  "title": "short plan title",
                  "description": "2 to 3 sentences that explicitly mention how and why the plan was adapted",
                  "exercises": [
                    { "name": "exercise name", "sets": number_of_sets, "repetitions": number_of_repetitions }
                  ]
                }

                All text content (title, description and exercise names) MUST be written in English.
                Propose between 4 and 8 exercises.
                """.formatted(
                profile.getFitnessGoal(),
                profile.getFitnessLevel(),
                targetFrequency,
                plan.getTitle(),
                plan.getDescription(),
                currentExercises.toString(),
                targetFrequency,
                progressLines.toString()
        );
    }

    private record GeminiResponse(List<Candidate> candidates) {}
    private record Candidate(Content content) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}

    private record GeneratedPlan(String title, String description, List<GeneratedExercise> exercises) {}
    private record GeneratedExercise(String name, Integer sets, Integer repetitions) {}
}
