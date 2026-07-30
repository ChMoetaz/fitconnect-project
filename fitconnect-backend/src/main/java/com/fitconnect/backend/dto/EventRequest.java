package com.fitconnect.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** Input for creating an event. The owning {@code groupId} comes from the path, never the body. */
@Data
public class EventRequest {
    @NotBlank
    private String title;

    private String description;

    /** Date and time of the event, ISO-8601 (e.g. "2026-08-01T18:00:00"). */
    @NotNull
    private LocalDateTime eventDate;

    private String location;
}
