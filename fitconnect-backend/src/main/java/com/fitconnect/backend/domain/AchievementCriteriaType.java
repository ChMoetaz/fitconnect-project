package com.fitconnect.backend.domain;

/**
 * How an {@link Achievement} is earned. The concrete metric each value maps to is computed in
 * {@code AchievementService} from the user's {@link ProgressRecord} history.
 */
public enum AchievementCriteriaType {

    /** Cumulative sum of {@code completedWorkouts} across all of the user's progress records. */
    WORKOUTS_COMPLETED,

    /** Number of distinct calendar days on which the user logged a progress record. */
    STREAK_DAYS
}
