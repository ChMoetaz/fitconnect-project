package com.fitconnect.backend.dto;

import com.fitconnect.backend.domain.Achievement;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Output view of an achievement definition (the catalogue exposed by GET /api/achievements). */
@Data
@AllArgsConstructor
public class AchievementResponse {
    private Long achievementId;
    private String name;
    private String description;
    private String criteriaType;
    private int criteriaThreshold;

    public static AchievementResponse from(Achievement a) {
        return new AchievementResponse(
                a.getAchievementId(),
                a.getName(),
                a.getDescription(),
                a.getCriteriaType().name(),
                a.getCriteriaThreshold());
    }
}
