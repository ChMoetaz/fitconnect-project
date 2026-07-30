package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.domain.UserProfile;
import com.fitconnect.backend.dto.OnboardingRequest;
import com.fitconnect.backend.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserProfileRepository userProfileRepository;
    private final UserService userService;
    private final SportTypeService sportTypeService;

    /**
     * Creates or updates a user's onboarding profile
     * (goal, level, frequency, main sport).
     */
    @Transactional
    public UserProfile submitOnboarding(Long userId, OnboardingRequest request) {
        User user = userService.getById(userId);

        UserProfile profile = userProfileRepository.findByUser_UserId(userId)
                .orElseGet(() -> UserProfile.builder().user(user).build());

        profile.setFitnessGoal(request.getFitnessGoal());
        profile.setFitnessLevel(request.getFitnessLevel());
        profile.setTrainingFrequency(request.getTrainingFrequency());

        // the sportType is attached to the profile via the first generated training plan,
        // here we just resolve/create it for later use if needed
        SportType ignored = sportTypeService.getOrCreate(request.getSportTypeName());

        return userProfileRepository.save(profile);
    }
}
