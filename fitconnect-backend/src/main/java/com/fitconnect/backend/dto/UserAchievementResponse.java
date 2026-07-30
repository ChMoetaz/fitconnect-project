package com.fitconnect.backend.dto;

import com.fitconnect.backend.domain.Achievement;
import com.fitconnect.backend.domain.UserAchievement;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/**
 * Output view of an earned achievement: the badge details flattened together with the date it was
 * obtained. Flattening here (rather than returning the {@link UserAchievement} entity) keeps the
 * back-reference to {@code User} out of the JSON and sidesteps any lazy-serialization concern.
 */
@Data
@AllArgsConstructor
public class UserAchievementResponse {
    private Long achievementId;
    private String name;
    private String description;
    private String criteriaType;
    private int criteriaThreshold;
    private LocalDate earnedAt;

    public static UserAchievementResponse from(UserAchievement ua) {
        Achievement a = ua.getAchievement();
        return new UserAchievementResponse(
                a.getAchievementId(),
                a.getName(),
                a.getDescription(),
                a.getCriteriaType().name(),
                a.getCriteriaThreshold(),
                ua.getEarnedAt());
    }
}
