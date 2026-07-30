package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.Event;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.EventRequest;
import com.fitconnect.backend.dto.EventResponse;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.CommunityGroupRepository;
import com.fitconnect.backend.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EventService} — repositories/UserService mocked, no database. Covers the
 * happy path and the error branches (group/event not found, event not belonging to the path group),
 * and that register/unregister mutate the owning {@code attendees} side and save the Event.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private CommunityGroupRepository communityGroupRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private EventService eventService;

    private static final Long GROUP_ID = 7L;
    private static final Long EVENT_ID = 50L;

    private CommunityGroup group() {
        return CommunityGroup.builder().communityId(GROUP_ID).name("Berlin Runners").build();
    }

    private Event eventInGroup() {
        Event event = Event.builder()
                .eventId(EVENT_ID).title("Group Run")
                .eventDate(LocalDateTime.of(2026, 8, 1, 10, 0))
                .communityGroup(group())
                .build();
        event.setAttendees(new HashSet<>());
        return event;
    }

    private EventRequest request() {
        EventRequest request = new EventRequest();
        request.setTitle("Group Run");
        request.setDescription("10k");
        request.setEventDate(LocalDateTime.of(2026, 8, 1, 10, 0));
        request.setLocation("Tiergarten");
        return request;
    }

    @Test
    void createEvent_buildsEventLinkedToGroup() {
        when(communityGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        EventResponse response = eventService.createEvent(GROUP_ID, request());

        assertThat(response.getTitle()).isEqualTo("Group Run");
        assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(response.getAttendeeCount()).isZero();
    }

    @Test
    void createEvent_throwsWhenGroupMissing() {
        when(communityGroupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.createEvent(GROUP_ID, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Group not found");
        verify(eventRepository, never()).save(any());
    }

    @Test
    void getEventsByGroup_throwsWhenGroupMissing() {
        when(communityGroupRepository.existsById(GROUP_ID)).thenReturn(false);

        assertThatThrownBy(() -> eventService.getEventsByGroup(GROUP_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(eventRepository, never()).findByGroupWithAttendees(any());
    }

    @Test
    void getEventsByGroup_mapsEventsToDto() {
        when(communityGroupRepository.existsById(GROUP_ID)).thenReturn(true);
        when(eventRepository.findByGroupWithAttendees(GROUP_ID)).thenReturn(List.of(eventInGroup()));

        List<EventResponse> responses = eventService.getEventsByGroup(GROUP_ID);

        assertThat(responses).singleElement()
                .satisfies(r -> assertThat(r.getEventId()).isEqualTo(EVENT_ID));
    }

    @Test
    void getEvent_throwsWhenEventMissing() {
        when(eventRepository.findByIdWithAttendees(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent(GROUP_ID, EVENT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Event not found");
    }

    @Test
    void getEvent_throwsWhenEventBelongsToAnotherGroup() {
        when(eventRepository.findByIdWithAttendees(EVENT_ID)).thenReturn(Optional.of(eventInGroup()));

        // Event is in group 7, but the path says group 99 → 404 (mismatched nesting).
        assertThatThrownBy(() -> eventService.getEvent(99L, EVENT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("does not belong to group");
    }

    @Test
    void deleteEvent_deletesWhenFoundInGroup() {
        Event event = eventInGroup();
        when(eventRepository.findByIdWithAttendees(EVENT_ID)).thenReturn(Optional.of(event));

        eventService.deleteEvent(GROUP_ID, EVENT_ID);

        verify(eventRepository).delete(event);
    }

    @Test
    void register_addsAttendeeToOwningSideAndSaves() {
        Event event = eventInGroup();
        User user = User.builder().userId(1L).email("john@fitconnect.test").role("USER").build();
        when(eventRepository.findByIdWithAttendees(EVENT_ID)).thenReturn(Optional.of(event));
        when(userService.getById(1L)).thenReturn(user);
        when(eventRepository.save(event)).thenReturn(event);

        EventResponse response = eventService.register(GROUP_ID, EVENT_ID, 1L);

        assertThat(event.getAttendees()).containsExactly(user);
        assertThat(response.getAttendeeCount()).isEqualTo(1);
        assertThat(response.getAttendeeIds()).containsExactly(1L);
        verify(eventRepository).save(event);
    }

    @Test
    void unregister_removesAttendeeAndSaves() {
        Event event = eventInGroup();
        User user = User.builder().userId(1L).email("john@fitconnect.test").role("USER").build();
        event.getAttendees().add(user);
        when(eventRepository.findByIdWithAttendees(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        EventResponse response = eventService.unregister(GROUP_ID, EVENT_ID, 1L);

        assertThat(event.getAttendees()).isEmpty();
        assertThat(response.getAttendeeCount()).isZero();
        verify(eventRepository).save(event);
    }
}
