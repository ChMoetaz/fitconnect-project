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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AchievementService} — repositories mocked, no database. Focuses on the
 * award logic: crossing a cumulative-workout threshold, skipping already-earned badges, the
 * below-threshold no-op, and the read/mapping methods.
 */
@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock
    private AchievementRepository achievementRepository;
    @Mock
    private UserAchievementRepository userAchievementRepository;
    @Mock
    private ProgressRecordRepository progressRecordRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private AchievementService achievementService;

    private static final User USER = User.builder().userId(1L).email("john@fitconnect.test").role("USER").build();

    private Achievement workoutBadge(long id, String name, int threshold) {
        return Achievement.builder()
                .achievementId(id).name(name)
                .criteriaType(AchievementCriteriaType.WORKOUTS_COMPLETED)
                .criteriaThreshold(threshold)
                .build();
    }

    private ProgressRecord workouts(int completed, LocalDate date) {
        return ProgressRecord.builder().date(date).completedWorkouts(completed).build();
    }

    @Test
    void checkAndAward_awardsEveryBadgeAtOrBelowTheCurrentTotal() {
        Achievement first = workoutBadge(1L, "First Workout", 1);
        Achievement consistency = workoutBadge(2L, "Consistency", 10);
        Achievement dedicated = workoutBadge(3L, "Dedicated", 50);
        when(achievementRepository.findAll()).thenReturn(List.of(first, consistency, dedicated));
        // Total completed workouts = 6 + 4 = 10 → First Workout (1) and Consistency (10) unlocked, Dedicated (50) not.
        when(progressRecordRepository.findByUser_UserIdOrderByDateDesc(1L)).thenReturn(List.of(
                workouts(6, LocalDate.of(2026, 7, 20)),
                workouts(4, LocalDate.of(2026, 7, 21))));
        when(userAchievementRepository.existsByUser_UserIdAndAchievement_AchievementId(anyLong(), anyLong()))
                .thenReturn(false);
        when(userService.getById(1L)).thenReturn(USER);

        List<Achievement> awarded = achievementService.checkAndAwardAchievements(1L);

        assertThat(awarded).extracting(Achievement::getName)
                .containsExactlyInAnyOrder("First Workout", "Consistency");

        ArgumentCaptor<UserAchievement> captor = ArgumentCaptor.forClass(UserAchievement.class);
        verify(userAchievementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(ua -> {
            assertThat(ua.getUser()).isSameAs(USER);
            assertThat(ua.getEarnedAt()).isEqualTo(LocalDate.now());
        });
    }

    @Test
    void checkAndAward_skipsAlreadyEarnedBadges() {
        Achievement first = workoutBadge(1L, "First Workout", 1);
        when(achievementRepository.findAll()).thenReturn(List.of(first));
        when(progressRecordRepository.findByUser_UserIdOrderByDateDesc(1L))
                .thenReturn(List.of(workouts(5, LocalDate.of(2026, 7, 20))));
        // Threshold met, but the badge is already earned → nothing new is written.
        when(userAchievementRepository.existsByUser_UserIdAndAchievement_AchievementId(1L, 1L))
                .thenReturn(true);

        List<Achievement> awarded = achievementService.checkAndAwardAchievements(1L);

        assertThat(awarded).isEmpty();
        verify(userAchievementRepository, never()).save(any());
        verify(userService, never()).getById(anyLong());
    }

    @Test
    void checkAndAward_awardsNothingBelowThreshold() {
        Achievement consistency = workoutBadge(2L, "Consistency", 10);
        when(achievementRepository.findAll()).thenReturn(List.of(consistency));
        when(progressRecordRepository.findByUser_UserIdOrderByDateDesc(1L))
                .thenReturn(List.of(workouts(3, LocalDate.of(2026, 7, 20))));
        when(userAchievementRepository.existsByUser_UserIdAndAchievement_AchievementId(1L, 2L))
                .thenReturn(false);

        List<Achievement> awarded = achievementService.checkAndAwardAchievements(1L);

        assertThat(awarded).isEmpty();
        verify(userAchievementRepository, never()).save(any());
    }

    @Test
    void checkAndAward_countsDistinctDaysForStreakBadge() {
        Achievement streak = Achievement.builder()
                .achievementId(5L).name("Three Active Days")
                .criteriaType(AchievementCriteriaType.STREAK_DAYS)
                .criteriaThreshold(3)
                .build();
        when(achievementRepository.findAll()).thenReturn(List.of(streak));
        // 4 records but only 3 distinct dates → metric = 3, threshold met.
        when(progressRecordRepository.findByUser_UserIdOrderByDateDesc(1L)).thenReturn(List.of(
                workouts(1, LocalDate.of(2026, 7, 20)),
                workouts(1, LocalDate.of(2026, 7, 20)),
                workouts(1, LocalDate.of(2026, 7, 21)),
                workouts(1, LocalDate.of(2026, 7, 22))));
        when(userAchievementRepository.existsByUser_UserIdAndAchievement_AchievementId(1L, 5L))
                .thenReturn(false);
        when(userService.getById(1L)).thenReturn(USER);

        List<Achievement> awarded = achievementService.checkAndAwardAchievements(1L);

        assertThat(awarded).extracting(Achievement::getName).containsExactly("Three Active Days");
    }

    @Test
    void checkAndAward_noAchievementsDefined_returnsEmptyWithoutTouchingProgress() {
        when(achievementRepository.findAll()).thenReturn(List.of());

        assertThat(achievementService.checkAndAwardAchievements(1L)).isEmpty();
        verify(progressRecordRepository, never()).findByUser_UserIdOrderByDateDesc(anyLong());
        verify(userAchievementRepository, never()).save(any());
    }

    @Test
    void getAllAchievements_mapsToDto() {
        when(achievementRepository.findAll()).thenReturn(List.of(workoutBadge(1L, "First Workout", 1)));

        List<AchievementResponse> responses = achievementService.getAllAchievements();

        assertThat(responses).singleElement().satisfies(r -> {
            assertThat(r.getName()).isEqualTo("First Workout");
            assertThat(r.getCriteriaType()).isEqualTo("WORKOUTS_COMPLETED");
            assertThat(r.getCriteriaThreshold()).isEqualTo(1);
        });
    }

    @Test
    void getEarnedAchievements_flattensBadgeAndEarnedDate() {
        Achievement first = workoutBadge(1L, "First Workout", 1);
        UserAchievement ua = UserAchievement.builder()
                .userAchievementId(10L).user(USER).achievement(first)
                .earnedAt(LocalDate.of(2026, 7, 23))
                .build();
        when(userAchievementRepository.findByUser_UserId(1L)).thenReturn(List.of(ua));

        List<UserAchievementResponse> responses = achievementService.getEarnedAchievements(1L);

        assertThat(responses).singleElement().satisfies(r -> {
            assertThat(r.getAchievementId()).isEqualTo(1L);
            assertThat(r.getName()).isEqualTo("First Workout");
            assertThat(r.getEarnedAt()).isEqualTo(LocalDate.of(2026, 7, 23));
        });
    }
}
