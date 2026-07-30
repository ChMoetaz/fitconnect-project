package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.CoachProfile;
import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.dto.CoachProfileRequest;
import com.fitconnect.backend.dto.CoachProfileResponse;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.CoachProfileRepository;
import com.fitconnect.backend.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoachRecommendationService {

    private final CoachProfileRepository coachProfileRepository;
    private final SportTypeService sportTypeService;
    private final GeocodingService geocodingService;

    // Reads map to CoachProfileResponse INSIDE the transaction (sportTypes are JOIN FETCH-ed), so the
    // raw entity / its relations are never serialized — consistent with CommunityGroupResponse.

    @Transactional(readOnly = true)
    public List<CoachProfileResponse> getAllCoaches() {
        return coachProfileRepository.findAllWithSportTypes().stream()
                .map(CoachProfileResponse::from)
                .toList();
    }

    /**
     * Simple recommendation (business rule) based on the requested sport.
     * Will be enhanced later by the AI service (preference-based recommendation).
     */
    @Transactional(readOnly = true)
    public List<CoachProfileResponse> recommendBySportType(Long sportTypeId) {
        return coachProfileRepository.findBySportTypes_SportTypeId(sportTypeId).stream()
                .map(CoachProfileResponse::from)
                .toList();
    }

    /**
     * Coaches within {@code radiusKm} of the given point. Only coaches that were successfully geocoded
     * (non-null lat/lng) can match; distance is a Haversine great-circle computed in Java (no PostGIS).
     */
    @Transactional(readOnly = true)
    public List<CoachProfileResponse> findNearby(double lat, double lng, double radiusKm) {
        return coachProfileRepository.findAllWithSportTypes().stream()
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .filter(c -> GeoUtils.haversineKm(lat, lng, c.getLatitude(), c.getLongitude()) <= radiusKm)
                .map(CoachProfileResponse::from)
                .toList();
    }

    @Transactional
    public CoachProfileResponse createCoach(CoachProfileRequest request) {
        Set<SportType> sportTypes = request.getSportTypeNames() == null
                ? Set.of()
                : request.getSportTypeNames().stream()
                    .map(sportTypeService::getOrCreate)
                    .collect(Collectors.toSet());

        CoachProfile coach = CoachProfile.builder()
                .name(request.getName())
                .specialization(request.getSpecialization())
                .experienceYears(request.getExperienceYears())
                .location(request.getLocation())
                .sportTypes(sportTypes)
                .build();

        // Best-effort geocoding (never throws): leaves lat/lng null if the address can't be resolved,
        // so coach creation is not blocked by a Maps outage / missing key.
        geocodingService.geocode(request.getLocation()).ifPresent(point -> {
            coach.setLatitude(point.latitude());
            coach.setLongitude(point.longitude());
        });

        return CoachProfileResponse.from(coachProfileRepository.save(coach));
    }

    @Transactional(readOnly = true)
    public CoachProfileResponse getById(Long coachId) {
        CoachProfile coach = coachProfileRepository.findByIdWithSportTypes(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("Coach not found: " + coachId));
        return CoachProfileResponse.from(coach);
    }

    /**
     * Admin update of a coach's scalar fields (name / specialization / experienceYears / location).
     * If {@code location} actually changes, lat/lng are reset and the new address is re-geocoded
     * (best-effort, never throws). The {@code sportTypes} association is intentionally left untouched
     * here (the request may still carry {@code sportTypeNames}, but this endpoint only edits the
     * scalar fields). Loaded with a JOIN FETCH so the DTO mapping sees the sportTypes.
     */
    @Transactional
    public CoachProfileResponse updateCoach(Long coachId, CoachProfileRequest request) {
        CoachProfile coach = coachProfileRepository.findByIdWithSportTypes(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("Coach not found: " + coachId));

        coach.setName(request.getName());
        coach.setSpecialization(request.getSpecialization());
        coach.setExperienceYears(request.getExperienceYears());

        String newLocation = request.getLocation();
        boolean locationChanged = !java.util.Objects.equals(coach.getLocation(), newLocation);
        coach.setLocation(newLocation);
        if (locationChanged) {
            // Reset first so stale coordinates never survive a failed/blank re-geocode.
            coach.setLatitude(null);
            coach.setLongitude(null);
            geocodingService.geocode(newLocation).ifPresent(point -> {
                coach.setLatitude(point.latitude());
                coach.setLongitude(point.longitude());
            });
        }

        return CoachProfileResponse.from(coachProfileRepository.save(coach));
    }

    /**
     * Admin delete of a coach. Deleting the entity is enough to clear the {@code coach_sport_types}
     * join table: {@code CoachProfile} is the OWNING side of that {@code @ManyToMany} (it holds the
     * {@code @JoinTable}), so Hibernate removes the join rows automatically. The referenced
     * {@code SportType}s are shared reference data and are NOT deleted.
     */
    @Transactional
    public void deleteCoach(Long coachId) {
        CoachProfile coach = coachProfileRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("Coach not found: " + coachId));
        coachProfileRepository.delete(coach);
    }
}
