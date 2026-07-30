package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.EventRequest;
import com.fitconnect.backend.dto.EventResponse;
import com.fitconnect.backend.repository.CommunityGroupRepository;
import com.fitconnect.backend.repository.EventRepository;
import com.fitconnect.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence-level regression test for event attendee register/unregister — the same spirit as
 * {@code CommunityServiceJoinTest}: it asserts on the ACTUAL {@code event_attendees} join-table row
 * count via a native query, not on any HTTP status or in-memory collection. This is exactly the
 * check that would have caught bug 4b (saving the wrong side of a {@code @ManyToMany}) — here
 * {@code Event} is the owning side, so the row must really be written.
 *
 * <p>Runs as a {@code @DataJpaTest} slice on in-memory H2 (no Docker), with the service wired by
 * hand from the sliced repositories, off the full application context.
 */
@DataJpaTest
class EventServiceAttendeeTest {

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private CommunityGroupRepository communityGroupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, new BCryptPasswordEncoder());
        eventService = new EventService(eventRepository, communityGroupRepository, userService);
    }

    private EventRequest sampleRequest() {
        EventRequest request = new EventRequest();
        request.setTitle("Saturday Group Run");
        request.setDescription("10k around the park");
        request.setEventDate(LocalDateTime.of(2026, 8, 1, 10, 0));
        request.setLocation("Tiergarten");
        return request;
    }

    @Test
    void register_insertsRowIntoJoinTable() {
        User user = userRepository.save(User.builder()
                .email("runner@fitconnect.test").password("irrelevant").role("USER").build());
        CommunityGroup group = communityGroupRepository.save(CommunityGroup.builder()
                .name("Berlin Runners").build());
        EventResponse event = eventService.createEvent(group.getCommunityId(), sampleRequest());

        eventService.register(group.getCommunityId(), event.getEventId(), user.getUserId());

        assertThat(countAttendeeRows(event.getEventId(), user.getUserId()))
                .as("event_attendees must contain exactly one row after a register")
                .isEqualTo(1L);
    }

    @Test
    void unregister_removesRowFromJoinTable() {
        User user = userRepository.save(User.builder()
                .email("quitter@fitconnect.test").password("irrelevant").role("USER").build());
        CommunityGroup group = communityGroupRepository.save(CommunityGroup.builder()
                .name("Berlin Cyclists").build());
        EventResponse event = eventService.createEvent(group.getCommunityId(), sampleRequest());

        eventService.register(group.getCommunityId(), event.getEventId(), user.getUserId());
        assertThat(countAttendeeRows(event.getEventId(), user.getUserId())).isEqualTo(1L);

        eventService.unregister(group.getCommunityId(), event.getEventId(), user.getUserId());

        assertThat(countAttendeeRows(event.getEventId(), user.getUserId()))
                .as("event_attendees must contain no row after an unregister")
                .isZero();
    }

    /** Reads the join table directly so the assertion cannot be fooled by an in-memory-only change. */
    private long countAttendeeRows(Long eventId, Long userId) {
        entityManager.flush();
        entityManager.clear();
        Number count = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM event_attendees "
                        + "WHERE event_id = :eventId AND user_id = :userId")
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .getSingleResult();
        return count.longValue();
    }
}
