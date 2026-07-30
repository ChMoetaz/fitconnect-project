package com.fitconnect.backend.controller;

import com.fitconnect.backend.dto.EventRequest;
import com.fitconnect.backend.dto.EventResponse;
import com.fitconnect.backend.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Fitness events nested under a community group. Requires a valid JWT (any authenticated user),
 * consistent with the community-group endpoints — no per-user ownership check, and {@code userId}
 * is passed as a query param on register/unregister, exactly like {@code .../join} / {@code .../leave}.
 */
@RestController
@RequestMapping("/api/community-groups/{groupId}/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventResponse>> list(@PathVariable Long groupId) {
        return ResponseEntity.ok(eventService.getEventsByGroup(groupId));
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(
            @PathVariable Long groupId,
            @Valid @RequestBody EventRequest request) {
        EventResponse created = eventService.createEvent(groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> get(@PathVariable Long groupId, @PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getEvent(groupId, eventId));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(@PathVariable Long groupId, @PathVariable Long eventId) {
        eventService.deleteEvent(groupId, eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/register")
    public ResponseEntity<EventResponse> register(
            @PathVariable Long groupId, @PathVariable Long eventId, @RequestParam Long userId) {
        return ResponseEntity.ok(eventService.register(groupId, eventId, userId));
    }

    @PostMapping("/{eventId}/unregister")
    public ResponseEntity<EventResponse> unregister(
            @PathVariable Long groupId, @PathVariable Long eventId, @RequestParam Long userId) {
        return ResponseEntity.ok(eventService.unregister(groupId, eventId, userId));
    }
}
