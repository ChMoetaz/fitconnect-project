package com.fitconnect.backend.repository;

import com.fitconnect.backend.domain.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    /** Used by the seeder to stay idempotent (only insert a starter achievement if absent). */
    boolean existsByName(String name);
}
