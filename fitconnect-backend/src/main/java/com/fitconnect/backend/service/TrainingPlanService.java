package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.TrainingPlan;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.TrainingPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainingPlanService {

    private final TrainingPlanRepository trainingPlanRepository;

    @Transactional(readOnly = true)
    public List<TrainingPlan> getPlansForUser(Long userId) {
        return trainingPlanRepository.findByUser_UserIdWithExercises(userId);
    }

    @Transactional(readOnly = true)
    public TrainingPlan getById(Long planId) {
        return trainingPlanRepository.findByIdWithExercises(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Training plan not found: " + planId));
    }

    /**
     * Persists the plan (and its exercises, via cascade) assembled by AiTrainingPlanService
     * from the Gemini generation.
     */
    @Transactional
    public TrainingPlan save(TrainingPlan plan) {
        return trainingPlanRepository.save(plan);
    }

    @Transactional
    public void deletePlan(Long planId) {
        if (!trainingPlanRepository.existsById(planId)) {
            throw new ResourceNotFoundException("Training plan not found: " + planId);
        }
        trainingPlanRepository.deleteById(planId);
    }
}
