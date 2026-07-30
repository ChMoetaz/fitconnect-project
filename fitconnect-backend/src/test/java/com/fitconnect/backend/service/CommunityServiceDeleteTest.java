package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.Event;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.exception.ResourceNotFoundException;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@code CommunityService.deleteGroup} actually removes the group AND all rows that a
 * foreign key would otherwise keep pointing at it — the group row itself, its events, its
 * event_attendees join rows, and its user_community_groups membership rows — while leaving the
 * users themselves untouched.
 *
 * <p>Asserts on the ACTUAL table row counts (native queries), not on a return value / HTTP status,
 * in the same spirit as {@code CommunityServiceJoinTest}: a plain {@code deleteById} would 200/204
 * and then blow up on a constraint violation, or silently leave orphan join rows — only counting
 * the rows catches that. {@code @DataJpaTest} slice on H2, no Docker / full context needed.
 */
@DataJpaTest
class CommunityServiceDeleteTest {

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
    void deleteGroup_removesGroupAndAllDependents_butKeepsUsers() {
        User member = userRepository.save(User.builder()
                .email("member@fitconnect.test").password("x").role("USER").build());
        CommunityGroup group = communityGroupRepository.save(CommunityGroup.builder()
                .name("Berlin Runners").build());

        // A membership row (user_community_groups) ...
        communityService.joinGroup(group.getCommunityId(), member.getUserId());
        // ... and an event with an attendee (events + event_attendees).
        Event event = Event.builder()
                .title("Saturday Run")
                .eventDate(LocalDateTime.now().plusDays(1))
                .communityGroup(group)
                .build();
        event.getAttendees().add(member);
        event = eventRepository.save(event);

        Long groupId = group.getCommunityId();
        Long eventId = event.getEventId();
        Long userId = member.getUserId();

        // Sanity: everything is really there before the delete.
        assertThat(count("SELECT COUNT(*) FROM community_groups WHERE community_id = " + groupId)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM events WHERE community_group_id = " + groupId)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM user_community_groups WHERE community_group_id = " + groupId)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM event_attendees WHERE event_id = " + eventId)).isEqualTo(1L);

        communityService.deleteGroup(groupId);

        // The group and every row that referenced it are gone ...
        assertThat(count("SELECT COUNT(*) FROM community_groups WHERE community_id = " + groupId))
                .as("group row must be deleted").isZero();
        assertThat(count("SELECT COUNT(*) FROM events WHERE community_group_id = " + groupId))
                .as("the group's events must be deleted").isZero();
        assertThat(count("SELECT COUNT(*) FROM user_community_groups WHERE community_group_id = " + groupId))
                .as("membership rows must be deleted").isZero();
        assertThat(count("SELECT COUNT(*) FROM event_attendees WHERE event_id = " + eventId))
                .as("event_attendees rows must be deleted").isZero();

        // ... but the user is a shared entity and must survive.
        assertThat(count("SELECT COUNT(*) FROM users WHERE user_id = " + userId))
                .as("the member itself must NOT be deleted").isEqualTo(1L);
    }

    @Test
    void deleteGroup_missing_throwsNotFound() {
        assertThatThrownBy(() -> communityService.deleteGroup(9999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Reads the DB directly so the assertion cannot be fooled by an in-memory-only change. */
    private long count(String sql) {
        entityManager.flush();
        entityManager.clear();
        Number count = (Number) entityManager.getEntityManager()
                .createNativeQuery(sql)
                .getSingleResult();
        return count.longValue();
    }
}
