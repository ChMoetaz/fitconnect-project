package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.Achievement;
import com.fitconnect.backend.domain.AchievementCriteriaType;
import com.fitconnect.backend.domain.ProgressRecord;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.domain.UserAchievement;
import com.fitconnect.backend.dto.AchievementResponse;
import com.fitconnect.backend.dto.UserAchievementResponse;
import com.fitconnect.backend.repository.AchievementRepository;
import com.fitconnect.backend.repository.ProgressRecordRepository;
import com.fitconnect.backend.repository.UserAchievementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Achievement (badge) logic: exposes the catalogue and a user's earned badges, and — the core of
 * the feature — evaluates the unlock criteria against the user's real {@link ProgressRecord}
 * history and awards any newly-earned badges.
 *
 * <p>{@link #checkAndAwardAchievements} is invoked by {@code ProgressTrackingService.addRecord}
 * right after a record is saved, so badges appear automatically as the user logs progress.
 */
@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final ProgressRecordRepository progressRecordRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<AchievementResponse> getAllAchievements() {
        return achievementRepository.findAll().stream()
                .map(AchievementResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserAchievementResponse> getEarnedAchievements(Long userId) {
        return userAchievementRepository.findByUser_UserId(userId).stream()
                .map(UserAchievementResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Re-evaluates every achievement the user has NOT yet earned against their current progress
     * metrics and persists a {@link UserAchievement} for each newly satisfied one. Idempotent:
     * already-earned badges are skipped, so calling it repeatedly never awards a badge twice.
     *
     * @return the achievements awarded by THIS call (empty if none crossed their threshold now)
     */
    @Transactional
    public List<Achievement> checkAndAwardAchievements(Long userId) {
        List<Achievement> all = achievementRepository.findAll();
        if (all.isEmpty()) {
            return List.of();
        }

        List<ProgressRecord> history = progressRecordRepository.findByUser_UserIdOrderByDateDesc(userId);
        long totalWorkouts = totalCompletedWorkouts(history);
        long distinctDays = distinctActiveDays(history);

        List<Achievement> newlyAwarded = new ArrayList<>();
        User user = null; // resolved lazily, only if we actually award something
        for (Achievement achievement : all) {
            boolean alreadyEarned = userAchievementRepository
                    .existsByUser_UserIdAndAchievement_AchievementId(userId, achievement.getAchievementId());
            if (alreadyEarned) {
                continue;
            }
            long metric = metricFor(achievement.getCriteriaType(), totalWorkouts, distinctDays);
            if (metric >= achievement.getCriteriaThreshold()) {
                if (user == null) {
                    user = userService.getById(userId);
                }
                userAchievementRepository.save(UserAchievement.builder()
                        .user(user)
                        .achievement(achievement)
                        .earnedAt(LocalDate.now())
                        .build());
                newlyAwarded.add(achievement);
            }
        }
        return newlyAwarded;
    }

    private long metricFor(AchievementCriteriaType type, long totalWorkouts, long distinctDays) {
        return switch (type) {
            case WORKOUTS_COMPLETED -> totalWorkouts;
            case STREAK_DAYS -> distinctDays;
        };
    }

    private long totalCompletedWorkouts(List<ProgressRecord> history) {
        return history.stream()
                .mapToLong(r -> r.getCompletedWorkouts() != null ? r.getCompletedWorkouts() : 0)
                .sum();
    }

    private long distinctActiveDays(List<ProgressRecord> history) {
        return history.stream()
                .map(ProgressRecord::getDate)
                .distinct()
                .count();
    }
}
