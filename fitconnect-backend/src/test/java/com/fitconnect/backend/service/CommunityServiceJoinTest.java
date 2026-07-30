package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.repository.CommunityGroupRepository;
import com.fitconnect.backend.repository.EventRepository;
import com.fitconnect.backend.repository.SportTypeRepository;
import com.fitconnect.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the community-join bug: {@code POST .../join} returned 200 but the
 * {@code user_community_groups} join table stayed empty, because the service saved the
 * inverse (mappedBy) side of the {@code @ManyToMany} instead of the owning side.
 *
 * <p>This test asserts on the ACTUAL join-table row count (via a native query), not on the
 * HTTP status — a 200-only assertion is exactly what hid the bug in the first place.
 *
 * <p>Runs as a {@code @DataJpaTest} slice against an in-memory H2 database, so it needs no
 * Docker/Postgres. The service is wired by hand from the sliced repositories, which keeps the
 * test off the full application context (no JWT secret / Gemini key required to boot).
 */
@DataJpaTest
class CommunityServiceJoinTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityGroupRepository communityGroupRepository;
    @Autowired
    private SportTypeRepository sportTypeRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TestEntityManager entityManager;

    private CommunityService communityService;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, new BCryptPasswordEncoder());
        SportTypeService sportTypeService = new SportTypeService(sportTypeRepository);
        // These tests never call createGroup, so geocoding is never invoked — a bare mock is enough.
        GeocodingService geocodingService = org.mockito.Mockito.mock(GeocodingService.class);
        communityService = new CommunityService(
                communityGroupRepository, userRepository, eventRepository, userService, sportTypeService,
                geocodingService);
    }

    @Test
    void joinGroup_insertsRowIntoJoinTable() {
        User user = userRepository.save(User.builder()
                .email("runner@fitconnect.test")
                .password("irrelevant")
                .role("USER")
                .build());
        CommunityGroup group = communityGroupRepository.save(CommunityGroup.builder()
                .name("Berlin Runners")
                .build());

        communityService.joinGroup(group.getCommunityId(), user.getUserId());

        assertThat(countJoinRows(user.getUserId(), group.getCommunityId()))
                .as("user_community_groups must contain exactly one row after a join")
                .isEqualTo(1L);
    }

    @Test
    void leaveGroup_removesRowFromJoinTable() {
        User user = userRepository.save(User.builder()
                .email("quitter@fitconnect.test")
                .password("irrelevant")
                .role("USER")
                .build());
        CommunityGroup group = communityGroupRepository.save(CommunityGroup.builder()
                .name("Berlin Cyclists")
                .build());

        communityService.joinGroup(group.getCommunityId(), user.getUserId());
        assertThat(countJoinRows(user.getUserId(), group.getCommunityId())).isEqualTo(1L);

        communityService.leaveGroup(group.getCommunityId(), user.getUserId());

        assertThat(countJoinRows(user.getUserId(), group.getCommunityId()))
                .as("user_community_groups must contain no row after a leave")
                .isZero();
    }

    /** Reads the join table directly so the assertion cannot be fooled by an in-memory-only change. */
    private long countJoinRows(Long userId, Long groupId) {
        entityManager.flush();
        entityManager.clear();
        Number count = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM user_community_groups "
                        + "WHERE user_id = :userId AND community_group_id = :groupId")
                .setParameter("userId", userId)
                .setParameter("groupId", groupId)
                .getSingleResult();
        return count.longValue();
    }
}