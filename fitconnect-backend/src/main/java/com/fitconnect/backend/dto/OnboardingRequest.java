package com.fitconnect.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OnboardingRequest {
    @NotBlank
    private String fitnessGoal;

    @NotBlank
    private String fitnessLevel;

    @NotNull
    private Integer trainingFrequency;

    /** nom du sport principal, optionnel */
    private String sportTypeName;
}
