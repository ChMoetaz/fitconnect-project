package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.Event;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.EventRequest;
import com.fitconnect.backend.dto.EventResponse;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.CommunityGroupRepository;
import com.fitconnect.backend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Community fitness events tied to a {@link CommunityGroup}: CRUD plus attendee register/unregister.
 * Reads go through {@code JOIN FETCH} queries so the {@code attendees} collection is initialized
 * before the DTO mapping (open-in-view is off). Attendee registration mutates and saves the OWNING
 * side ({@code Event.attendees}), so the {@code event_attendees} join table is actually written —
 * see the bidirectional-relations convention / resolved bug 4b.
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final CommunityGroupRepository communityGroupRepository;
    private final UserService userService;

    @Transactional
    public EventResponse createEvent(Long groupId, EventRequest request) {
        CommunityGroup group = communityGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .eventDate(request.getEventDate())
                .location(request.getLocation())
                .communityGroup(group)
                .build();

        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByGroup(Long groupId) {
        requireGroupExists(groupId);
        return eventRepository.findByGroupWithAttendees(groupId).stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long groupId, Long eventId) {
        return EventResponse.from(loadEventInGroup(groupId, eventId));
    }

    @Transactional
    public void deleteEvent(Long groupId, Long eventId) {
        // Validates the event exists AND belongs to the group before deleting. Deleting the owning
        // side also clears its event_attendees rows (Hibernate removes the join rows first).
        Event event = loadEventInGroup(groupId, eventId);
        eventRepository.delete(event);
    }

    @Transactional
    public EventResponse register(Long groupId, Long eventId, Long userId) {
        Event event = loadEventInGroup(groupId, eventId);
        User user = userService.getById(userId);

        // Owning side: mutate + save the Event so the event_attendees join row is persisted.
        event.getAttendees().add(user);
        eventRepository.save(event);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse unregister(Long groupId, Long eventId, Long userId) {
        Event event = loadEventInGroup(groupId, eventId);

        // Remove-by-id on the owning side; a no-op if the user was not registered.
        event.getAttendees().removeIf(u -> u.getUserId().equals(userId));
        eventRepository.save(event);
        return EventResponse.from(event);
    }

    /** Loads an event with its attendees and asserts it belongs to the group in the path (else 404). */
    private Event loadEventInGroup(Long groupId, Long eventId) {
        Event event = eventRepository.findByIdWithAttendees(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (!event.getCommunityGroup().getCommunityId().equals(groupId)) {
            throw new ResourceNotFoundException(
                    "Event " + eventId + " does not belong to group " + groupId);
        }
        return event;
    }

    private void requireGroupExists(Long groupId) {
        if (!communityGroupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group not found: " + groupId);
        }
    }
}
