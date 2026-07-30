package com.fitconnect.backend.controller;

import com.fitconnect.backend.dto.CoachRecommendationResponse;
import com.fitconnect.backend.dto.CommunityGroupRecommendationResponse;
import com.fitconnect.backend.security.CurrentUser;
import com.fitconnect.backend.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI-personalized recommendation endpoints, keyed on the authenticated user's profile. These are
 * ADDITIONS, not replacements: the classic non-AI endpoints ({@code GET /api/coaches},
 * {@code /api/coaches/recommend?sportTypeId=}, {@code /api/coaches/nearby},
 * {@code GET /api/community-groups}, {@code /api/community-groups/nearby}) are untouched and remain the
 * way to browse the full catalogue without going through Gemini.
 *
 * <p>Both routes are {@code /api/users/{userId}/...} and therefore JWT + self-guarded via
 * {@code CurrentUser.requireSelf} (same style as {@code ProgressController} / {@code OnboardingController}):
 * a user can only get recommendations for their own profile.
 */
@RestController
@RequestMapping("/api/users/{userId}")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/coaches/recommended")
    public ResponseEntity<List<CoachRecommendationResponse>> recommendedCoaches(@PathVariable Long userId) {
        CurrentUser.requireSelf(userId);
        return ResponseEntity.ok(recommendationService.recommendCoaches(userId));
    }

    @GetMapping("/community-groups/recommended")
    public ResponseEntity<List<CommunityGroupRecommendationResponse>> recommendedGroups(@PathVariable Long userId) {
        CurrentUser.requireSelf(userId);
        return ResponseEntity.ok(recommendationService.recommendGroups(userId));
    }
}
