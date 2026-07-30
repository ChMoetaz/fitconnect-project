package com.fitconnect.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A community fitness event (group session, competition, ...) belonging to a {@link CommunityGroup},
 * with an optional set of {@link User} attendees.
 *
 * <p><b>Owning side of the attendees {@code @ManyToMany}</b>: unlike {@code CommunityGroup.members}
 * (which is the inverse {@code mappedBy} side and was the source of resolved bug 4b), {@code Event}
 * itself declares the {@code @JoinTable "event_attendees"} and there is NO inverse collection on
 * {@code User}. Making the relation unidirectional and owned here means registering an attendee is
 * simply "mutate {@code event.getAttendees()} and save the Event" — the owning side — so the join
 * table is always written; there is no wrong (mappedBy) side to accidentally save.
 *
 * <p>{@code communityGroup} is a LAZY {@code @JsonIgnore} back-reference (same pattern as
 * {@code TrainingPlan.user} / {@code ProgressRecord.user}); {@code attendees} is LAZY +
 * {@code @JsonIgnore} and only ever loaded through a {@code JOIN FETCH} query (see
 * {@code EventRepository}) so it is never serialized as a Hibernate proxy — avoiding the
 * LazyInitializationException class of bug with {@code open-in-view: false}.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    private String location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_group_id", nullable = false)
    @JsonIgnore
    private CommunityGroup communityGroup;

    @ManyToMany
    @JoinTable(
            name = "event_attendees",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    @JsonIgnore
    private Set<User> attendees = new HashSet<>();
}
