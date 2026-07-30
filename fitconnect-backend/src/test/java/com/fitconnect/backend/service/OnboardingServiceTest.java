package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.domain.UserProfile;
import com.fitconnect.backend.dto.OnboardingRequest;
import com.fitconnect.backend.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OnboardingService}. Verifies the create-or-update branch of the
 * profile (new profile linked to the user vs. update of an existing one) and that the sport
 * type name is resolved through {@link SportTypeService}.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserService userService;
    @Mock
    private SportTypeService sportTypeService;

    @InjectMocks
    private OnboardingService onboardingService;

    private OnboardingRequest request(String goal, String level, Integer freq, String sport) {
        OnboardingRequest r = new OnboardingRequest();
        r.setFitnessGoal(goal);
        r.setFitnessLevel(level);
        r.setTrainingFrequency(freq);
        r.setSportTypeName(sport);
        return r;
    }

    @Test
    void submitOnboarding_createsProfileWhenNoneExists() {
        User user = User.builder().userId(1L).email("john@fitconnect.test").role("USER").build();
        when(userService.getById(1L)).thenReturn(user);
        when(userProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.empty());
        when(sportTypeService.getOrCreate("Running")).thenReturn(SportType.builder().sportTypeId(2L).name("Running").build());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfile saved = onboardingService.submitOnboarding(1L, request("MUSCLE_GAIN", "BEGINNER", 3, "Running"));

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getFitnessGoal()).isEqualTo("MUSCLE_GAIN");
        assertThat(saved.getFitnessLevel()).isEqualTo("BEGINNER");
        assertThat(saved.getTrainingFrequency()).isEqualTo(3);
        verify(sportTypeService).getOrCreate("Running");
    }

    @Test
    void submitOnboarding_updatesExistingProfileInPlace() {
        User user = User.builder().userId(1L).email("john@fitconnect.test").role("USER").build();
        UserProfile existing = UserProfile.builder()
                .profileId(7L).user(user)
                .fitnessGoal("WEIGHT_LOSS").fitnessLevel("BEGINNER").trainingFrequency(2)
                .build();
        when(userService.getById(1L)).thenReturn(user);
        when(userProfileRepository.findByUser_UserId(1L)).thenReturn(Optional.of(existing));
        when(sportTypeService.getOrCreate(any())).thenReturn(null);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        onboardingService.submitOnboarding(1L, request("ENDURANCE", "ADVANCED", 5, null));

        verify(userProfileRepository).save(captor.capture());
        UserProfile persisted = captor.getValue();
        // Same row reused (id preserved), fields overwritten with the new onboarding values.
        assertThat(persisted.getProfileId()).isEqualTo(7L);
        assertThat(persisted.getFitnessGoal()).isEqualTo("ENDURANCE");
        assertThat(persisted.getFitnessLevel()).isEqualTo("ADVANCED");
        assertThat(persisted.getTrainingFrequency()).isEqualTo(5);
    }
}