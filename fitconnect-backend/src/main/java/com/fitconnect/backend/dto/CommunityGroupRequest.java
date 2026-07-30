package com.fitconnect.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommunityGroupRequest {
    @NotBlank
    private String name;

    private String description;

    private String location;

    private String sportTypeName;
}
