package com.fitconnect.backend.controller;

import com.fitconnect.backend.domain.UserProfile;
import com.fitconnect.backend.dto.OnboardingRequest;
import com.fitconnect.backend.security.CurrentUser;
import com.fitconnect.backend.service.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping
    public ResponseEntity<UserProfile> submitOnboarding(
            @PathVariable Long userId,
            @Valid @RequestBody OnboardingRequest request) {
        CurrentUser.requireSelf(userId);
        UserProfile profile = onboardingService.submitOnboarding(userId, request);
        return ResponseEntity.ok(profile);
    }
}
