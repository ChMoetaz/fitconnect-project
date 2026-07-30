package com.fitconnect.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long profileId;

    /** e.g. "WEIGHT_LOSS", "MUSCLE_GAIN", "ENDURANCE" */
    private String fitnessGoal;

    /** e.g. "BEGINNER", "INTERMEDIATE", "ADVANCED" */
    private String fitnessLevel;

    private Integer trainingFrequency;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}
