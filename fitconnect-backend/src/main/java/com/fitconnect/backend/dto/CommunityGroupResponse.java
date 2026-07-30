package com.fitconnect.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.User;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Output view of a community group. The {@code members} {@code @ManyToMany} stays {@code @JsonIgnore}
 * on the entity (it is the inverse/mappedBy side, and exposing it would risk a
 * LazyInitializationException / infinite serialization — see CLAUDE.md). Instead it is flattened here
 * into {@code memberCount} + {@code isJoined}, computed inside the service transaction from a group
 * whose {@code members} were {@code JOIN FETCH}-ed. {@code isJoined} tells whether the currently
 * authenticated user is one of those members.
 */
@Data
@AllArgsConstructor
public class CommunityGroupResponse {
    private Long communityId;
    private String name;
    private String description;
    private String location;
    private Long sportTypeId;
    private String sportTypeName;
    // Nullable — geocoded from location on create; null when geocoding was skipped/failed.
    private Double latitude;
    private Double longitude;
    private int memberCount;
    // Field is boolean isJoined -> Lombok getter isJoined() -> Jackson would name it "joined".
    // Pin the JSON key to "isJoined" as required by the API contract.
    @JsonProperty("isJoined")
    private boolean isJoined;

    public static CommunityGroupResponse from(CommunityGroup group, Long currentUserId) {
        boolean joined = group.getMembers().stream()
                .map(User::getUserId)
                .anyMatch(id -> id.equals(currentUserId));
        return new CommunityGroupResponse(
                group.getCommunityId(),
                group.getName(),
                group.getDescription(),
                group.getLocation(),
                group.getSportType() != null ? group.getSportType().getSportTypeId() : null,
                group.getSportType() != null ? group.getSportType().getName() : null,
                group.getLatitude(),
                group.getLongitude(),
                group.getMembers().size(),
                joined);
    }
}
