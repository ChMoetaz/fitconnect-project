package com.fitconnect.backend.dto;

import com.fitconnect.backend.domain.Event;
import com.fitconnect.backend.domain.User;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Output view of an event. The {@code attendees} {@code @ManyToMany} is flattened to
 * {@code attendeeCount} + {@code attendeeIds} (rather than serializing full {@code User}s), which
 * keeps passwords/relations out of the JSON and avoids exposing the entity graph. Built inside the
 * service transaction, from an Event whose attendees were {@code JOIN FETCH}-ed.
 */
@Data
@AllArgsConstructor
public class EventResponse {
    private Long eventId;
    private String title;
    private String description;
    private LocalDateTime eventDate;
    private String location;
    private Long groupId;
    private int attendeeCount;
    private List<Long> attendeeIds;

    public static EventResponse from(Event event) {
        List<Long> attendeeIds = event.getAttendees().stream()
                .map(User::getUserId)
                .sorted()
                .toList();
        return new EventResponse(
                event.getEventId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventDate(),
                event.getLocation(),
                event.getCommunityGroup().getCommunityId(),
                attendeeIds.size(),
                attendeeIds);
    }
}
