package com.fitconnect.backend.dto;

import lombok.Data;

@Data
public class GenerateTrainingPlanRequest {
    private String sportTypeName;
    private Integer durationWeeks;
}
