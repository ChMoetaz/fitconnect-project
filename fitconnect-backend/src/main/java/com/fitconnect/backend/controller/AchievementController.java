package com.fitconnect.backend.controller;

import com.fitconnect.backend.dto.AchievementResponse;
import com.fitconnect.backend.dto.UserAchievementResponse;
import com.fitconnect.backend.security.CurrentUser;
import com.fitconnect.backend.service.AchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Two read endpoints, on two different base paths, so both live in one controller (no class-level
 * {@code @RequestMapping}):
 * <ul>
 *   <li>{@code GET /api/achievements} — the full catalogue (any authenticated user);</li>
 *   <li>{@code GET /api/users/{userId}/achievements} — a user's earned badges (JWT + self).</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping("/api/achievements")
    public ResponseEntity<List<AchievementResponse>> getAllAchievements() {
        return ResponseEntity.ok(achievementService.getAllAchievements());
    }

    @GetMapping("/api/users/{userId}/achievements")
    public ResponseEntity<List<UserAchievementResponse>> getUserAchievements(@PathVariable Long userId) {
        CurrentUser.requireSelf(userId);
        return ResponseEntity.ok(achievementService.getEarnedAchievements(userId));
    }
}
