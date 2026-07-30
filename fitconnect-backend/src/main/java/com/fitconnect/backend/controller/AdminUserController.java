package com.fitconnect.backend.controller;

import com.fitconnect.backend.dto.UpdateUserRoleRequest;
import com.fitconnect.backend.dto.UserResponse;
import com.fitconnect.backend.security.CurrentUser;
import com.fitconnect.backend.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only user management. Every route is JWT + admin: {@code CurrentUser.requireAdmin()} is the
 * first line of each method (same explicit style as {@code CurrentUser.requireSelf} on the per-user
 * routes) — a non-admin gets a 403, an unauthenticated caller a 401 (from the security filter chain).
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> list() {
        CurrentUser.requireAdmin();
        return ResponseEntity.ok(adminUserService.getAllUsers());
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<UserResponse> updateRole(
            @PathVariable Long userId, @Valid @RequestBody UpdateUserRoleRequest request) {
        CurrentUser.requireAdmin();
        return ResponseEntity.ok(adminUserService.updateRole(userId, request.getRole()));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable Long userId) {
        CurrentUser.requireAdmin();
        adminUserService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
