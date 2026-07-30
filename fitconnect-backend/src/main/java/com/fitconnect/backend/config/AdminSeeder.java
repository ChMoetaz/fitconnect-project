package com.fitconnect.backend.config;

import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures a default administrator account exists at startup, idempotently — same spirit as
 * {@link AchievementSeeder}, and runs in every environment (real Postgres and the H2 test context).
 *
 * <p><b>Ensure, not just create-if-absent.</b> A plain {@code existsByEmail} guard is not enough: the
 * email {@code admin@gmail.com} may already exist as an ordinary {@code USER} (e.g. it was registered
 * via {@code POST /api/users/register}, which always forces {@code role="USER"}) before this seeder was
 * introduced. In that case a create-if-absent seeder would skip it and the account would stay a
 * non-admin forever. So instead: create it as {@code ADMIN} if missing, and otherwise PROMOTE an
 * existing same-email account to {@code ADMIN} if it isn't already. Once it is {@code ADMIN}, this is a
 * no-op — still fully idempotent across restarts, and it never touches any other account. The password
 * is BCrypt-hashed like any other account. Further admins can then be promoted via
 * {@code PATCH /api/admin/users/{userId}/role}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin@gmail.com";
    private static final String ADMIN_PASSWORD = "azerty123";
    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElse(null);

        if (admin == null) {
            userRepository.save(User.builder()
                    .email(ADMIN_EMAIL)
                    .password(passwordEncoder.encode(ADMIN_PASSWORD))
                    .role(ADMIN_ROLE)
                    .build());
            log.info("Seeded default admin account ({})", ADMIN_EMAIL);
        } else if (!ADMIN_ROLE.equals(admin.getRole())) {
            // A pre-existing same-email account (e.g. a plain USER) is promoted to ADMIN — this is the
            // case that a create-if-absent seeder would have missed. Password is left as-is.
            admin.setRole(ADMIN_ROLE);
            userRepository.save(admin);
            log.info("Promoted existing account {} to {}", ADMIN_EMAIL, ADMIN_ROLE);
        }
        // else: already ADMIN → nothing to do (idempotent).
    }
}
