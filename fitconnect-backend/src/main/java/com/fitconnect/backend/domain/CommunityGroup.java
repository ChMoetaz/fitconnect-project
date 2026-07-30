package com.fitconnect.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "community_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long communityId;

    @Column(nullable = false)
    private String name;

    private String description;

    private String location;

    /** Nullable: geocoded from {@link #location} by GeocodingService on create; null if it fails. */
    private Double latitude;
    private Double longitude;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sport_type_id")
    private SportType sportType;

    @ManyToMany(mappedBy = "communityGroups")
    @Builder.Default
    @JsonIgnore
    private Set<User> members = new HashSet<>();
}
