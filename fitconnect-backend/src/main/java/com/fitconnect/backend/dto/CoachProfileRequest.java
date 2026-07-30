package com.fitconnect.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class CoachProfileRequest {
    @NotBlank
    private String name;

    private String specialization;

    private Integer experienceYears;

    /** Free-text address; geocoded into latitude/longitude at creation (best-effort). */
    private String location;

    private Set<String> sportTypeNames;
}
