package com.fitconnect.backend.dto;

import com.fitconnect.backend.domain.CoachProfile;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Comparator;
import java.util.List;

/**
 * Output view of a coach. Replaces exposing the raw {@code CoachProfile} entity: it flattens the
 * {@code sportTypes} {@code @ManyToMany} to a small id+name list (built inside the service
 * transaction from a JOIN FETCH-ed coach, so no lazy-serialization risk) and adds the geocoded
 * {@code latitude}/{@code longitude} (nullable — filled by GeocodingService after creation).
 */
@Data
@AllArgsConstructor
public class CoachProfileResponse {
    private Long coachId;
    private String name;
    private String specialization;
    private Integer experienceYears;
    private String location;
    private Double latitude;
    private Double longitude;
    private List<SportTypeRef> sportTypes;

    public static CoachProfileResponse from(CoachProfile coach) {
        List<SportTypeRef> sports = coach.getSportTypes().stream()
                .map(s -> new SportTypeRef(s.getSportTypeId(), s.getName()))
                .sorted(Comparator.comparing(SportTypeRef::sportTypeId))
                .toList();
        return new CoachProfileResponse(
                coach.getCoachId(),
                coach.getName(),
                coach.getSpecialization(),
                coach.getExperienceYears(),
                coach.getLocation(),
                coach.getLatitude(),
                coach.getLongitude(),
                sports);
    }

    /** A sport type flattened to id + name (same shape idea as CommunityGroupResponse's sportType). */
    public record SportTypeRef(Long sportTypeId, String name) {}
}
