package com.fitconnect.backend.controller;

import com.fitconnect.backend.domain.TrainingPlan;
import com.fitconnect.backend.dto.GenerateTrainingPlanRequest;
import com.fitconnect.backend.security.CurrentUser;
import com.fitconnect.backend.service.AiTrainingPlanService;
import com.fitconnect.backend.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/training-plans")
@RequiredArgsConstructor
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;
    private final AiTrainingPlanService aiTrainingPlanService;

    @GetMapping
    public ResponseEntity<List<TrainingPlan>> getPlans(@PathVariable Long userId) {
        CurrentUser.requireSelf(userId);
        return ResponseEntity.ok(trainingPlanService.getPlansForUser(userId));
    }

    @PostMapping
    public ResponseEntity<TrainingPlan> createPlan(
            @PathVariable Long userId,
            @RequestBody(required = false) GenerateTrainingPlanRequest request) {
        CurrentUser.requireSelf(userId);
        TrainingPlan plan = aiTrainingPlanService.generatePlan(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @GetMapping("/{planId}")
    public ResponseEntity<TrainingPlan> getPlan(@PathVariable Long userId, @PathVariable Long planId) {
        CurrentUser.requireSelf(userId);
        return ResponseEntity.ok(trainingPlanService.getById(planId));
    }

    /**
     * Adaptive training plan: re-tunes the existing plan (same planId) from the user's real
     * progress history via Gemini — see AiTrainingPlanService.adaptPlan.
     */
    @PostMapping("/{planId}/adapt")
    public ResponseEntity<TrainingPlan> adaptPlan(@PathVariable Long userId, @PathVariable Long planId) {
        CurrentUser.requireSelf(userId);
        return ResponseEntity.ok(aiTrainingPlanService.adaptPlan(userId, planId));
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long userId, @PathVariable Long planId) {
        CurrentUser.requireSelf(userId);
        trainingPlanService.deletePlan(planId);
        return ResponseEntity.noContent().build();
    }
}
