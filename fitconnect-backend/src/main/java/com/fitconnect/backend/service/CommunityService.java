package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.Event;
import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.CommunityGroupRequest;
import com.fitconnect.backend.dto.CommunityGroupResponse;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.CommunityGroupRepository;
import com.fitconnect.backend.repository.EventRepository;
import com.fitconnect.backend.repository.UserRepository;
import com.fitconnect.backend.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityGroupRepository communityGroupRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final UserService userService;
    private final SportTypeService sportTypeService;
    private final GeocodingService geocodingService;

    // Mapped to CommunityGroupResponse inside the transaction (members are JOIN FETCH-ed) so the
    // @JsonIgnore members collection is never serialized — only memberCount / isJoined leak out.
    // currentUserId (from CurrentUser in the controller) drives the isJoined flag.
    @Transactional(readOnly = true)
    public List<CommunityGroupResponse> getAllGroups(Long currentUserId) {
        return communityGroupRepository.findAllWithMembers().stream()
                .map(group -> CommunityGroupResponse.from(group, currentUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommunityGroupResponse> getGroupsBySport(Long sportTypeId, Long currentUserId) {
        return communityGroupRepository.findBySportType_SportTypeIdWithMembers(sportTypeId).stream()
                .map(group -> CommunityGroupResponse.from(group, currentUserId))
                .toList();
    }

    /**
     * Groups within {@code radiusKm} of the given point. Only groups that were successfully geocoded
     * (non-null lat/lng) can match; distance is a Haversine great-circle computed in Java (no PostGIS).
     */
    @Transactional(readOnly = true)
    public List<CommunityGroupResponse> findNearby(double lat, double lng, double radiusKm, Long currentUserId) {
        return communityGroupRepository.findAllWithMembers().stream()
                .filter(g -> g.getLatitude() != null && g.getLongitude() != null)
                .filter(g -> GeoUtils.haversineKm(lat, lng, g.getLatitude(), g.getLongitude()) <= radiusKm)
                .map(group -> CommunityGroupResponse.from(group, currentUserId))
                .toList();
    }

    @Transactional
    public CommunityGroup createGroup(CommunityGroupRequest request) {
        SportType sportType = sportTypeService.getOrCreate(request.getSportTypeName());

        CommunityGroup group = CommunityGroup.builder()
                .name(request.getName())
                .description(request.getDescription())
                .location(request.getLocation())
                .sportType(sportType)
                .build();

        // Best-effort geocoding: fills lat/lng when possible, silently leaves them null otherwise
        // (GeocodingService never throws), so a Maps outage never blocks group creation.
        geocodingService.geocode(request.getLocation()).ifPresent(point -> {
            group.setLatitude(point.latitude());
            group.setLongitude(point.longitude());
        });

        return communityGroupRepository.save(group);
    }

    /**
     * Admin update of a group's name / description / location / sportTypeName. If {@code location}
     * actually changes, lat/lng are reset and the new address is re-geocoded (best-effort, never
     * throws) — same enrichment as {@code createGroup}. Returns the raw entity, consistent with
     * {@code createGroup} (members stay {@code @JsonIgnore}; sportType is EAGER).
     */
    @Transactional
    public CommunityGroup updateGroup(Long communityId, CommunityGroupRequest request) {
        CommunityGroup group = communityGroupRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + communityId));

        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setSportType(sportTypeService.getOrCreate(request.getSportTypeName()));

        String newLocation = request.getLocation();
        boolean locationChanged = !java.util.Objects.equals(group.getLocation(), newLocation);
        group.setLocation(newLocation);
        if (locationChanged) {
            // Reset first so stale coordinates never survive a failed/blank re-geocode.
            group.setLatitude(null);
            group.setLongitude(null);
            geocodingService.geocode(newLocation).ifPresent(point -> {
                group.setLatitude(point.latitude());
                group.setLongitude(point.longitude());
            });
        }

        return communityGroupRepository.save(group);
    }

    /**
     * Deletes a group. Same not-found guard as {@code TrainingPlanService.deletePlan}, but a
     * CommunityGroup can't just be {@code deleteById}-ed: two FKs point at it and it owns no cascade,
     * so both dependents are cleared first, otherwise the DELETE hits a constraint violation.
     */
    @Transactional
    public void deleteGroup(Long communityId) {
        CommunityGroup group = communityGroupRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + communityId));

        // 1. events.community_group_id is NOT NULL and CommunityGroup owns no events collection.
        //    Delete the events as ENTITIES (not a bulk query) so Hibernate also removes their owned
        //    event_attendees join rows before deleting the events themselves.
        List<Event> events = eventRepository.findByGroupWithAttendees(communityId);
        eventRepository.deleteAll(events);

        // 2. user_community_groups rows: members is the inverse (mappedBy) side, so deleting the
        //    group would not touch them. Remove the group from each member's OWNING collection
        //    (User.communityGroups) and save the users — same owning-side rule as join/leave (bug 4b).
        for (User member : group.getMembers()) {
            member.getCommunityGroups().removeIf(g -> g.getCommunityId().equals(communityId));
        }
        userRepository.saveAll(group.getMembers());

        // 3. No FK references the group anymore → it can be deleted.
        communityGroupRepository.delete(group);
    }

    @Transactional
    public CommunityGroup joinGroup(Long groupId, Long userId) {
        CommunityGroup group = communityGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        User user = userService.getById(userId);

        // User.communityGroups is the OWNING side of the @ManyToMany (it carries the
        // @JoinTable "user_community_groups"). Only modifying+saving the owning side
        // actually writes the join-table row. Saving CommunityGroup (the mappedBy
        // inverse side) would return 200 but persist nothing — that was the bug.
        user.getCommunityGroups().add(group);
        userRepository.save(user);

        // Keep the in-memory inverse side consistent for any caller reading it in this tx.
        group.getMembers().add(user);
        return group;
    }

    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        CommunityGroup group = communityGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        User user = userService.getById(userId);

        // Same reason as joinGroup: remove from and save the owning side (User).
        user.getCommunityGroups().removeIf(g -> g.getCommunityId().equals(groupId));
        userRepository.save(user);

        group.getMembers().removeIf(u -> u.getUserId().equals(userId));
    }
}
