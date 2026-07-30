package com.fitconnect.backend.config;

import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for {@link AdminSeeder}. The seeder must ENSURE {@code admin@gmail.com} ends up as
 * {@code ADMIN}, not merely "create if the email is absent" — otherwise a pre-existing plain-USER
 * account with that email (which really happened in the dev DB) would stay a non-admin forever.
 *
 * <p>Wired by hand as a {@code @DataJpaTest} slice (same style as {@code CommunityServiceJoinTest}) so
 * it runs on in-memory H2 with no full context.
 */
@DataJpaTest
class AdminSeederTest {

    @Autowired
    private UserRepository userRepository;

    private AdminSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new AdminSeeder(userRepository, new BCryptPasswordEncoder());
    }

    @Test
    void createsAdminWhenAbsent() {
        seeder.run(null);

        User admin = userRepository.findByEmail("admin@gmail.com").orElseThrow();
        assertThat(admin.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void promotesPreexistingUserWithSameEmail() {
        // Simulates the dev-DB situation: admin@gmail.com already exists as a plain USER.
        userRepository.save(User.builder()
                .email("admin@gmail.com").password("hashed").role("USER").build());

        seeder.run(null);

        User admin = userRepository.findByEmail("admin@gmail.com").orElseThrow();
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        // No duplicate account was created.
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void isIdempotentWhenAlreadyAdmin() {
        seeder.run(null);
        seeder.run(null); // second boot

        assertThat(userRepository.findAll())
                .filteredOn(u -> "admin@gmail.com".equals(u.getEmail()))
                .singleElement()
                .satisfies(u -> assertThat(u.getRole()).isEqualTo("ADMIN"));
    }
}
