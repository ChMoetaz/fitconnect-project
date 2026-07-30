package com.fitconnect.backend.config;

import com.fitconnect.backend.domain.Achievement;
import com.fitconnect.backend.domain.AchievementCriteriaType;
import com.fitconnect.backend.repository.AchievementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the starter achievement catalogue at startup. Runs for every environment (real Postgres
 * and the H2 test context alike) and is idempotent — each badge is inserted only if a row with the
 * same name does not already exist — so restarts never create duplicates. A tiny code-based seeder
 * is enough here; a full migration tool (Flyway/Liquibase) would be overkill for reference data
 * this small on a student project.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AchievementSeeder implements ApplicationRunner {

    private final AchievementRepository achievementRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<Achievement> starters = List.of(
                Achievement.builder()
                        .name("First Workout")
                        .description("Complete your very first workout.")
                        .criteriaType(AchievementCriteriaType.WORKOUTS_COMPLETED)
                        .criteriaThreshold(1)
                        .build(),
                Achievement.builder()
                        .name("Consistency")
                        .description("Complete 10 workouts in total.")
                        .criteriaType(AchievementCriteriaType.WORKOUTS_COMPLETED)
                        .criteriaThreshold(10)
                        .build(),
                Achievement.builder()
                        .name("Dedicated")
                        .description("Complete 50 workouts in total.")
                        .criteriaType(AchievementCriteriaType.WORKOUTS_COMPLETED)
                        .criteriaThreshold(50)
                        .build());

        int inserted = 0;
        for (Achievement starter : starters) {
            if (!achievementRepository.existsByName(starter.getName())) {
                achievementRepository.save(starter);
                inserted++;
            }
        }
        if (inserted > 0) {
            log.info("Seeded {} starter achievement(s)", inserted);
        }
    }
}
