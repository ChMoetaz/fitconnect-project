package com.fitconnect.backend.controller;

import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.dto.CommunityGroupRequest;
import com.fitconnect.backend.dto.CommunityGroupResponse;
import com.fitconnect.backend.security.CurrentUser;
import com.fitconnect.backend.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community-groups")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping
    public ResponseEntity<List<CommunityGroupResponse>> getAll(
            @RequestParam(required = false) Long sportTypeId) {
        // A valid token is enough for this route (not tied to a specific userId), but we still read
        // the authenticated userId to compute isJoined per group.
        Long currentUserId = CurrentUser.getUserId();
        if (sportTypeId != null) {
            return ResponseEntity.ok(communityService.getGroupsBySport(sportTypeId, currentUserId));
        }
        return ResponseEntity.ok(communityService.getAllGroups(currentUserId));
    }

    @PostMapping
    public ResponseEntity<CommunityGroup> create(@Valid @RequestBody CommunityGroupRequest request) {
        CommunityGroup group = communityService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    /**
     * Admin-only: update a group's name/description/location/sportTypeName (re-geocodes on change).
     * Create/read/delete on this controller stay plain JWT (unchanged) — only this update is admin-gated.
     */
    @PutMapping("/{communityId}")
    public ResponseEntity<CommunityGroup> update(
            @PathVariable Long communityId, @Valid @RequestBody CommunityGroupRequest request) {
        CurrentUser.requireAdmin();
        return ResponseEntity.ok(communityService.updateGroup(communityId, request));
    }

    /** Groups with a geocoded location within radiusKm of (lat,lng); isJoined is per-caller. */
    @GetMapping("/nearby")
    public ResponseEntity<List<CommunityGroupResponse>> nearby(
            @RequestParam double lat, @RequestParam double lng, @RequestParam double radiusKm) {
        Long currentUserId = CurrentUser.getUserId();
        return ResponseEntity.ok(communityService.findNearby(lat, lng, radiusKm, currentUserId));
    }

    @DeleteMapping("/{communityId}")
    public ResponseEntity<Void> delete(@PathVariable Long communityId) {
        // JWT required (default rule), no self check — consistent with the other community-groups
        // routes, which are not tied to a specific userId.
        communityService.deleteGroup(communityId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/join")
    public ResponseEntity<CommunityGroup> join(@PathVariable Long groupId, @RequestParam Long userId) {
        return ResponseEntity.ok(communityService.joinGroup(groupId, userId));
    }

    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long groupId, @RequestParam Long userId) {
        communityService.leaveGroup(groupId, userId);
        return ResponseEntity.noContent().build();
    }
}
