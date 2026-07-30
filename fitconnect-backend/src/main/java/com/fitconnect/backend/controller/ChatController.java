package com.fitconnect.backend.controller;

import com.fitconnect.backend.dto.ChatMessageRequest;
import com.fitconnect.backend.dto.MessageResponse;
import com.fitconnect.backend.exception.ApiError;
import com.fitconnect.backend.exception.BadRequestException;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.security.WebSocketPrincipal;
import com.fitconnect.backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

/**
 * STOMP handler for the real-time group chat. Clients send to
 * {@code /app/community-groups/{groupId}/messages}; the persisted message is broadcast to every
 * subscriber of {@code /topic/community-groups/{groupId}/messages}.
 *
 * <p><b>Auth</b> is already done: the {@code Principal} was set at the WebSocket handshake from the
 * JWT (see {@code JwtHandshakeInterceptor}), so the sender identity here is trusted and taken from
 * the session — never from the payload (which is only {@code content}).
 *
 * <p><b>Error handling</b>: a business failure ({@code content} empty/too long, group missing, sender
 * not a member) must NOT kill the frame silently. It is caught and delivered to the sender alone on
 * {@code /user/queue/errors} as the same {@link ApiError} shape the REST side returns, so the frontend
 * can surface it in the chat UI. On success nothing is sent to that queue.
 */
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/community-groups/{groupId}/messages")
    public void send(@DestinationVariable Long groupId,
                     @Payload ChatMessageRequest request,
                     Principal principal) {
        Long senderId = ((WebSocketPrincipal) principal).getUserId();
        try {
            MessageResponse saved = messageService.saveMessage(groupId, senderId, request.getContent());
            messagingTemplate.convertAndSend(
                    "/topic/community-groups/" + groupId + "/messages", saved);
        } catch (BadRequestException ex) {
            sendError(principal, HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (ResourceNotFoundException ex) {
            sendError(principal, HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    /** Delivers a structured error to the sender's private queue ({@code /user/queue/errors}). */
    private void sendError(Principal principal, HttpStatus status, String message) {
        ApiError error = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message);
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", error);
    }
}
