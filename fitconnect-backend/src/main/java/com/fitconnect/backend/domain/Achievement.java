package com.fitconnect.backend.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * A badge/reward definition (reference data, like {@link SportType}): "First Workout",
 * "Consistency", etc. Seeded at startup (see {@code AchievementSeeder}) and never bound from
 * request input. Whether a user has earned it is recorded in {@link UserAchievement}.
 */
@Entity
@Table(name = "achievements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long achievementId;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    /** Persisted as its String name (not the ordinal) so reordering the enum never corrupts data. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AchievementCriteriaType criteriaType;

    /** The metric value (see {@link AchievementCriteriaType}) at or above which the badge is earned. */
    @Column(nullable = false)
    private int criteriaThreshold;
}
