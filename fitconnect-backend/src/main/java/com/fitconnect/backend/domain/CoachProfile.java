package com.fitconnect.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "coach_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long coachId;

    @Column(nullable = false)
    private String name;

    private String specialization;

    private Integer experienceYears;

    /** Free-text address (e.g. "Alexanderplatz, Berlin"), geocoded into latitude/longitude on create. */
    private String location;

    /** Nullable: filled by GeocodingService from {@link #location}; stays null if geocoding fails. */
    private Double latitude;
    private Double longitude;

    @ManyToMany
    @JoinTable(
            name = "coach_sport_types",
            joinColumns = @JoinColumn(name = "coach_id"),
            inverseJoinColumns = @JoinColumn(name = "sport_type_id")
    )
    @Builder.Default
    private Set<SportType> sportTypes = new HashSet<>();
}
