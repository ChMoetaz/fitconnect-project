package com.fitconnect.backend.repository;

import com.fitconnect.backend.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Events of a group, with their {@code attendees} eagerly fetched. Same {@code LEFT JOIN FETCH}
     * approach as {@code CoachProfileRepository.findAllWithSportTypes} / {@code TrainingPlanRepository
     * .findByUser_UserIdWithExercises}: the collection must be initialized before the session closes
     * (open-in-view is off) so the service can map it to a DTO. {@code DISTINCT} collapses the row
     * multiplication the join introduces.
     */
    @Query("SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.attendees "
            + "WHERE e.communityGroup.communityId = :groupId")
    List<Event> findByGroupWithAttendees(@Param("groupId") Long groupId);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.attendees WHERE e.eventId = :eventId")
    Optional<Event> findByIdWithAttendees(@Param("eventId") Long eventId);

    /**
     * Removes a user's rows from the {@code event_attendees} join table. That table is OWNED by
     * {@code Event} (not by {@code User}), so deleting a User does not cascade to it — leaving it would
     * fail on the {@code event_attendees.user_id} FK when an admin deletes a user who is registered to
     * an event. A targeted native delete is the simplest way to clear only the join rows (the events
     * and other attendees are untouched). Used by {@code AdminUserService.deleteUser}.
     */
    @Modifying
    @Query(value = "DELETE FROM event_attendees WHERE user_id = :userId", nativeQuery = true)
    void deleteAttendeeRowsByUserId(@Param("userId") Long userId);
}
