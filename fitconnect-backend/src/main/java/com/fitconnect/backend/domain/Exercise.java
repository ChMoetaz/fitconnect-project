package com.fitconnect.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exercises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long exerciseId;

    @Column(nullable = false)
    private String name;

    private Integer sets;

    private Integer repetitions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_plan_id", nullable = false)
    @JsonIgnore
    private TrainingPlan trainingPlan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sport_type_id")
    private SportType sportType;
}
