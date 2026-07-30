package com.fitconnect.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProgressRecordRequest {
    @NotNull
    private LocalDate date;

    private Integer completedWorkouts;

    private String notes;
}
