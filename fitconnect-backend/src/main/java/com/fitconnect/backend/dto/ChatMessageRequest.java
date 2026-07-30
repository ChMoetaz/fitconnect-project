package com.fitconnect.backend.dto;

import lombok.Data;

/**
 * Inbound WebSocket payload: what a client sends to {@code /app/community-groups/{groupId}/messages}.
 * Only carries {@code content} — the sender is NOT taken from the payload (it would be spoofable) but
 * from the authenticated WebSocket {@code Principal} (set at the handshake from the JWT), and the
 * target group comes from the STOMP destination. Content validation (blank / max length) is done
 * server-side in {@code MessageService} rather than via bean-validation on this DTO, so a violation
 * surfaces as a structured error on {@code /user/queue/errors} instead of a broken frame.
 */
@Data
public class ChatMessageRequest {
    private String content;
}
