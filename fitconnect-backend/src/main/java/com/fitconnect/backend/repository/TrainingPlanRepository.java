package com.fitconnect.backend.repository;

import com.fitconnect.backend.domain.TrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {

    @Query("SELECT DISTINCT t FROM TrainingPlan t LEFT JOIN FETCH t.exercises WHERE t.user.userId = :userId")
    List<TrainingPlan> findByUser_UserIdWithExercises(@Param("userId") Long userId);

    @Query("SELECT t FROM TrainingPlan t LEFT JOIN FETCH t.exercises WHERE t.planId = :planId")
    Optional<TrainingPlan> findByIdWithExercises(@Param("planId") Long planId);
}
