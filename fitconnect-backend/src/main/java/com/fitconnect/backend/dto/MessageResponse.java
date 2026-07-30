package com.fitconnect.backend.dto;

import com.fitconnect.backend.domain.Message;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * Output view of a chat {@link Message} — used both by the REST history endpoint and as the payload
 * broadcast over WebSocket to {@code /topic/community-groups/{groupId}/messages}. The raw entity is
 * never exposed: the {@code sender} {@code User} is flattened to {@code senderId} + {@code senderEmail}
 * (its {@code password} and relations stay out of the JSON), and the {@code communityGroup}
 * back-reference is reduced to {@code groupId}. Built inside the service transaction (via
 * {@link #from(Message)}), same static-factory style as {@code EventResponse.from} /
 * {@code CommunityGroupResponse.from}.
 */
@Data
@AllArgsConstructor
public class MessageResponse {
    private Long messageId;
    private Long groupId;
    private Long senderId;
    private String senderEmail;
    private String content;
    private Instant sentAt;

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getMessageId(),
                message.getCommunityGroup().getCommunityId(),
                message.getSender().getUserId(),
                message.getSender().getEmail(),
                message.getContent(),
                message.getSentAt());
    }
}
