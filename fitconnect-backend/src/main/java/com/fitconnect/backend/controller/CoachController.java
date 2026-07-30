package com.fitconnect.backend.controller;

import com.fitconnect.backend.dto.CoachProfileRequest;
import com.fitconnect.backend.dto.CoachProfileResponse;
import com.fitconnect.backend.security.CurrentUser;
import com.fitconnect.backend.service.CoachRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coaches")
@RequiredArgsConstructor
public class CoachController {

    private final CoachRecommendationService coachRecommendationService;

    @GetMapping
    public ResponseEntity<List<CoachProfileResponse>> getAll() {
        return ResponseEntity.ok(coachRecommendationService.getAllCoaches());
    }

    @GetMapping("/recommend")
    public ResponseEntity<List<CoachProfileResponse>> recommend(@RequestParam Long sportTypeId) {
        return ResponseEntity.ok(coachRecommendationService.recommendBySportType(sportTypeId));
    }

    /** Coaches with a geocoded location within radiusKm of (lat,lng). */
    @GetMapping("/nearby")
    public ResponseEntity<List<CoachProfileResponse>> nearby(
            @RequestParam double lat, @RequestParam double lng, @RequestParam double radiusKm) {
        return ResponseEntity.ok(coachRecommendationService.findNearby(lat, lng, radiusKm));
    }

    @PostMapping
    public ResponseEntity<CoachProfileResponse> create(@Valid @RequestBody CoachProfileRequest request) {
        CoachProfileResponse coach = coachRecommendationService.createCoach(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(coach);
    }

    @GetMapping("/{coachId}")
    public ResponseEntity<CoachProfileResponse> getById(@PathVariable Long coachId) {
        return ResponseEntity.ok(coachRecommendationService.getById(coachId));
    }

    /** Admin-only: update a coach's name/specialization/experienceYears/location (re-geocodes on change). */
    @PutMapping("/{coachId}")
    public ResponseEntity<CoachProfileResponse> update(
            @PathVariable Long coachId, @Valid @RequestBody CoachProfileRequest request) {
        CurrentUser.requireAdmin();
        return ResponseEntity.ok(coachRecommendationService.updateCoach(coachId, request));
    }

    /** Admin-only: delete a coach (clears the coach_sport_types join rows). */
    @DeleteMapping("/{coachId}")
    public ResponseEntity<Void> delete(@PathVariable Long coachId) {
        CurrentUser.requireAdmin();
        coachRecommendationService.deleteCoach(coachId);
        return ResponseEntity.noContent().build();
    }
}
