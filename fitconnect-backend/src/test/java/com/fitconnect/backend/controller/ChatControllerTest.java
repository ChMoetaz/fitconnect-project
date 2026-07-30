package com.fitconnect.backend.controller;

import com.fitconnect.backend.dto.ChatMessageRequest;
import com.fitconnect.backend.dto.MessageResponse;
import com.fitconnect.backend.exception.ApiError;
import com.fitconnect.backend.exception.BadRequestException;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.security.WebSocketPrincipal;
import com.fitconnect.backend.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the STOMP handler {@link ChatController} — {@link MessageService} and
 * {@link SimpMessagingTemplate} mocked. Verifies the two routing outcomes the handler is responsible
 * for: on success the saved message is broadcast to the group topic (and nothing goes to the error
 * queue); on a business failure a structured {@link ApiError} is delivered to the sender's private
 * queue (and nothing is broadcast). The STOMP wiring itself (handshake, broker) is exercised
 * manually — see the "Chat temps réel (WebSocket)" section of CLAUDE.md.
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private MessageService messageService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatController chatController;

    private static final Long GROUP_ID = 7L;
    private static final Long USER_ID = 3L;

    private ChatMessageRequest request(String content) {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent(content);
        return request;
    }

    @Test
    void send_broadcastsSavedMessageToGroupTopic() {
        WebSocketPrincipal principal = new WebSocketPrincipal(USER_ID);
        MessageResponse saved = new MessageResponse(
                100L, GROUP_ID, USER_ID, "runner@fitconnect.test", "hi", Instant.now());
        when(messageService.saveMessage(GROUP_ID, USER_ID, "hi")).thenReturn(saved);

        chatController.send(GROUP_ID, request("hi"), principal);

        verify(messagingTemplate)
                .convertAndSend("/topic/community-groups/7/messages", saved);
        // Nothing routed to the error queue on success.
        verify(messagingTemplate, never())
                .convertAndSendToUser(any(), any(), any());
    }

    @Test
    void send_routesBadRequestToUserErrorQueue() {
        WebSocketPrincipal principal = new WebSocketPrincipal(USER_ID);
        when(messageService.saveMessage(GROUP_ID, USER_ID, "hi"))
                .thenThrow(new BadRequestException("You must join the group before posting in its chat"));

        chatController.send(GROUP_ID, request("hi"), principal);

        // Not broadcast …
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
        // … but an ApiError (400) is sent to the sender's private queue.
        ArgumentCaptor<ApiError> errorCaptor = ArgumentCaptor.forClass(ApiError.class);
        verify(messagingTemplate)
                .convertAndSendToUser(eq("3"), eq("/queue/errors"), errorCaptor.capture());
        assertThat(errorCaptor.getValue().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(errorCaptor.getValue().getMessage()).contains("join the group");
    }

    @Test
    void send_routesNotFoundToUserErrorQueue() {
        WebSocketPrincipal principal = new WebSocketPrincipal(USER_ID);
        when(messageService.saveMessage(GROUP_ID, USER_ID, "hi"))
                .thenThrow(new ResourceNotFoundException("Group not found: 7"));

        chatController.send(GROUP_ID, request("hi"), principal);

        ArgumentCaptor<ApiError> errorCaptor = ArgumentCaptor.forClass(ApiError.class);
        verify(messagingTemplate)
                .convertAndSendToUser(eq("3"), eq("/queue/errors"), errorCaptor.capture());
        assertThat(errorCaptor.getValue().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
