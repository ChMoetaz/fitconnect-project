package com.fitconnect.backend.controller;

import com.fitconnect.backend.dto.MessageResponse;
import com.fitconnect.backend.security.CurrentUser;
import com.fitconnect.backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoint to load a group chat's history when the user opens the chat, before the WebSocket
 * connection takes over for live messages (see the "Chat temps réel (WebSocket)" section of
 * CLAUDE.md). Returns the last 50 messages, oldest-first.
 *
 * <p><b>Membership-guarded</b>: a valid token is not enough — the authenticated caller
 * ({@link CurrentUser#getUserId()}) must be a MEMBER of the group, otherwise
 * {@code MessageService.getHistory} throws {@code AccessDeniedException} → clean 403. This mirrors the
 * send side (a non-member cannot post) so the conversation is not readable by any authenticated user.
 * It is a per-caller check, hence done via {@code CurrentUser} rather than a static route whitelist.
 */
@RestController
@RequestMapping("/api/community-groups/{groupId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<List<MessageResponse>> history(@PathVariable Long groupId) {
        Long currentUserId = CurrentUser.getUserId();
        return ResponseEntity.ok(messageService.getHistory(groupId, currentUserId));
    }
}
