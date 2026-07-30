package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.ProgressRecord;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.ProgressRecordRequest;
import com.fitconnect.backend.repository.ProgressRecordRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProgressTrackingService}: the read delegates to the repository, and
 * {@code addRecord} links the new record to the user resolved via {@link UserService}.
 */
@ExtendWith(MockitoExtension.class)
class ProgressTrackingServiceTest {

    @Mock
    private ProgressRecordRepository progressRecordRepository;
    @Mock
    private UserService userService;
    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private ProgressTrackingService progressTrackingService;

    @Test
    void getRecordsForUser_delegatesToRepository() {
        List<ProgressRecord> expected = List.of(ProgressRecord.builder().recordId(1L).build());
        when(progressRecordRepository.findByUser_UserIdOrderByDateDesc(1L)).thenReturn(expected);

        assertThat(progressTrackingService.getRecordsForUser(1L)).isEqualTo(expected);
    }

    @Test
    void addRecord_buildsRecordLinkedToUser() {
        User user = User.builder().userId(1L).email("john@fitconnect.test").role("USER").build();
        when(userService.getById(1L)).thenReturn(user);
        when(progressRecordRepository.save(any(ProgressRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        ProgressRecordRequest request = new ProgressRecordRequest();
        request.setDate(LocalDate.of(2026, 7, 23));
        request.setCompletedWorkouts(4);
        request.setNotes("Good week");

        ProgressRecord saved = progressTrackingService.addRecord(1L, request);

        ArgumentCaptor<ProgressRecord> captor = ArgumentCaptor.forClass(ProgressRecord.class);
        verify(progressRecordRepository).save(captor.capture());
        ProgressRecord persisted = captor.getValue();
        assertThat(persisted.getUser()).isSameAs(user);
        assertThat(persisted.getDate()).isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(persisted.getCompletedWorkouts()).isEqualTo(4);
        assertThat(persisted.getNotes()).isEqualTo("Good week");
        assertThat(saved).isSameAs(persisted);
        // Adding a record must trigger the achievement re-evaluation for that user.
        verify(achievementService).checkAndAwardAchievements(1L);
    }
}