package com.fitconnect.backend.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Access to the current authenticated user (userId set as the principal by
 * JwtAuthenticationFilter) and ownership check for the "/api/users/{userId}/..." routes:
 * a valid token is not enough, the path userId must also match the token userId.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new AccessDeniedException("User not authenticated");
        }
        return userId;
    }

    /**
     * @throws AccessDeniedException if the authenticated user is not {@code pathUserId}
     *         — translated into a 403 by GlobalExceptionHandler.
     */
    public static void requireSelf(Long pathUserId) {
        if (!getUserId().equals(pathUserId)) {
            throw new AccessDeniedException("You cannot access another user's resources");
        }
    }

    /**
     * Ownership check by role instead of by id: requires the token's role to be {@code ADMIN}.
     * The role is carried as the authority {@code ROLE_ADMIN} by {@code JwtAuthenticationFilter}.
     * Same style as {@link #requireSelf} — called explicitly as the first line of each admin route.
     *
     * @throws AccessDeniedException if the caller is not an admin — translated into a 403 by
     *         GlobalExceptionHandler. (An unauthenticated caller never reaches here: the security
     *         filter chain already answers 401 for a missing/invalid token on a protected route.)
     */
    public static void requireAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            throw new AccessDeniedException("Admin privileges are required for this action");
        }
    }
}
