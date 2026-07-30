package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.UserResponse;
import com.fitconnect.backend.exception.BadRequestException;
import com.fitconnect.backend.repository.EventRepository;
import com.fitconnect.backend.repository.MessageRepository;
import com.fitconnect.backend.repository.UserAchievementRepository;
import com.fitconnect.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Admin-only user management (list / change role / delete). The authorization gate
 * ({@code CurrentUser.requireAdmin}) lives in the controller, consistently with how {@code requireSelf}
 * is called there for the per-user routes.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    /** Roles a user may be assigned via the admin PATCH endpoint. */
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN", "COACH");

    private final UserRepository userRepository;
    private final UserService userService;
    private final UserAchievementRepository userAchievementRepository;
    private final MessageRepository messageRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        // UserResponse exposes only id/email/role — never the password.
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateRole(Long userId, String role) {
        if (role == null || !ALLOWED_ROLES.contains(role)) {
            throw new BadRequestException(
                    "Invalid role '" + role + "'. Allowed values: " + ALLOWED_ROLES);
        }
        User user = userService.getById(userId); // 404 if the user does not exist
        user.setRole(role);
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Deletes a user and all its dependents so the delete never fails on a foreign-key constraint —
     * same spirit as {@code CommunityService.deleteGroup}. Two kinds of dependents:
     * <ul>
     *   <li><b>Cascaded from {@code User}</b> (nothing to do here): {@code UserProfile},
     *       {@code TrainingPlan} (+ its {@code Exercise}s) and {@code ProgressRecord} are mapped with
     *       {@code cascade = ALL, orphanRemoval = true}, and the owning-side {@code user_community_groups}
     *       join rows are removed automatically when the owning {@code User} is deleted.</li>
     *   <li><b>NOT cascaded from {@code User}</b> (cleared explicitly, else the FK blocks the delete):
     *       {@code UserAchievement} (FK user_id), {@code Message} (FK sender_id), and the
     *       {@code event_attendees} join rows (that table is owned by {@code Event}, not {@code User}).</li>
     * </ul>
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userService.getById(userId);

        // Owning-side memberships (user_community_groups): cleared explicitly on the owning collection,
        // per the owning-side rule used elsewhere (bug 4b). Hibernate would remove these join rows on
        // the delete anyway, but clearing keeps the object graph consistent within the transaction.
        user.getCommunityGroups().clear();

        // Dependents with a FK to the user that are NOT cascaded from User — remove them first.
        userAchievementRepository.deleteByUser_UserId(userId);
        messageRepository.deleteBySender_UserId(userId);
        eventRepository.deleteAttendeeRowsByUserId(userId);

        // Now no FK references the user anymore; cascade handles profile / plans / progress.
        userRepository.delete(user);
    }
}
