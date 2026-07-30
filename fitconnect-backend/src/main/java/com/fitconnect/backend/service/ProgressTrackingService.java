package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.ProgressRecord;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.ProgressRecordRequest;
import com.fitconnect.backend.repository.ProgressRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgressTrackingService {

    private final ProgressRecordRepository progressRecordRepository;
    private final UserService userService;
    private final AchievementService achievementService;

    @Transactional(readOnly = true)
    public List<ProgressRecord> getRecordsForUser(Long userId) {
        return progressRecordRepository.findByUser_UserIdOrderByDateDesc(userId);
    }

    @Transactional
    public ProgressRecord addRecord(Long userId, ProgressRecordRequest request) {
        User user = userService.getById(userId);

        ProgressRecord record = ProgressRecord.builder()
                .date(request.getDate())
                .completedWorkouts(request.getCompletedWorkouts())
                .notes(request.getNotes())
                .user(user)
                .build();

        ProgressRecord saved = progressRecordRepository.save(record);

        // A new record may push a cumulative metric past an achievement threshold — evaluate and
        // award any newly-earned badges in the same transaction (idempotent, see AchievementService).
        achievementService.checkAndAwardAchievements(userId);

        return saved;
    }
}
