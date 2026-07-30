package com.fitconnect.backend.repository;

import com.fitconnect.backend.domain.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    /**
     * The user's earned achievements. {@code UserAchievement.achievement} is EAGER, so the
     * referenced {@link com.fitconnect.backend.domain.Achievement} is loaded with each row — no
     * LazyInitializationException when the service maps them to DTOs after the session closes.
     */
    List<UserAchievement> findByUser_UserId(Long userId);

    boolean existsByUser_UserIdAndAchievement_AchievementId(Long userId, Long achievementId);

    /** Bulk-remove a user's earned badges — used by admin user deletion (no cascade from User). */
    void deleteByUser_UserId(Long userId);
}
