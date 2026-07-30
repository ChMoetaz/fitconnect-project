package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.Message;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.MessageResponse;
import com.fitconnect.backend.exception.BadRequestException;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.CommunityGroupRepository;
import com.fitconnect.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Persistence and history of the real-time group chat (see the "Chat temps réel (WebSocket)" section
 * of CLAUDE.md). Deliberately holds NO WebSocket concern: it just validates + persists a message and
 * reads the history, so it is trivially unit-testable and reusable by both the REST history endpoint
 * and the STOMP handler (which owns the broadcasting via {@code SimpMessagingTemplate}).
 *
 * <p><b>Membership rule</b>: {@link #saveMessage} enforces that the sender belongs to the target group
 * — you cannot post into a group you have not joined, the same "must be a member" spirit as
 * join/leave. The check reads the OWNING side ({@code User.communityGroups}, the join-table-backed
 * collection — resolved bug 4b) so it reflects the real membership, not a stale inverse view.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final CommunityGroupRepository communityGroupRepository;
    private final UserService userService;

    /**
     * Last 50 messages of a group, returned oldest-first for display. The repository query is
     * newest-first (so the {@code Top50} cap keeps the most recent ones), then reversed here.
     *
     * <p><b>Membership-guarded</b>: only a member of the group may read its chat history — the same
     * membership rule as {@link #saveMessage} (a non-member cannot post), applied to reads too so the
     * conversation is not readable by any authenticated user. The check reads the OWNING side
     * ({@code User.communityGroups}) via the shared {@link #isMember} helper. A non-member gets a clean
     * 403 (not a 500), consistent with {@code CurrentUser.requireSelf} which also throws
     * {@link AccessDeniedException} → handled by {@code GlobalExceptionHandler}.
     *
     * @throws ResourceNotFoundException if the group or the caller does not exist (clean 404).
     * @throws AccessDeniedException     if the caller is not a member of the group (clean 403).
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getHistory(Long groupId, Long currentUserId) {
        requireGroupExists(groupId);

        User caller = userService.getById(currentUserId);
        if (!isMember(caller, groupId)) {
            throw new AccessDeniedException("You must be a member of the group to read its chat");
        }

        List<Message> recentFirst =
                messageRepository.findTop50ByCommunityGroup_CommunityIdOrderBySentAtDesc(groupId);
        // Reverse to chronological order (oldest → newest) for the chat view.
        return recentFirst.stream()
                .sorted((a, b) -> a.getSentAt().compareTo(b.getSentAt()))
                .map(MessageResponse::from)
                .toList();
    }

    /**
     * Validates, persists and returns a message. Called by the STOMP handler after the sender has
     * already been authenticated (WebSocket Principal). Every failure is a business exception
     * ({@link BadRequestException} / {@link ResourceNotFoundException}) so the handler can turn it
     * into a structured error on {@code /user/queue/errors} rather than a broken frame.
     *
     * @throws BadRequestException      if the content is blank or exceeds {@link Message#MAX_CONTENT_LENGTH},
     *                                  or if the sender is not a member of the group.
     * @throws ResourceNotFoundException if the group or the sender does not exist.
     */
    @Transactional
    public MessageResponse saveMessage(Long groupId, Long senderId, String content) {
        String trimmed = content == null ? null : content.strip();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new BadRequestException("Message content must not be empty");
        }
        if (trimmed.length() > Message.MAX_CONTENT_LENGTH) {
            throw new BadRequestException(
                    "Message content must not exceed " + Message.MAX_CONTENT_LENGTH + " characters");
        }

        CommunityGroup group = communityGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        User sender = userService.getById(senderId);

        if (!isMember(sender, groupId)) {
            throw new BadRequestException("You must join the group before posting in its chat");
        }

        Message message = Message.builder()
                .content(trimmed)
                .sentAt(Instant.now())
                .sender(sender)
                .communityGroup(group)
                .build();

        return MessageResponse.from(messageRepository.save(message));
    }

    /**
     * Whether {@code user} belongs to the group, read on the OWNING side of the membership
     * {@code @ManyToMany} ({@code User.communityGroups}, the join-table-backed collection — resolved
     * bug 4b) so it reflects the real membership, not a stale inverse view. Shared by the send guard
     * ({@link #saveMessage}) and the read guard ({@link #getHistory}).
     */
    private boolean isMember(User user, Long groupId) {
        return user.getCommunityGroups().stream()
                .anyMatch(g -> g.getCommunityId().equals(groupId));
    }

    private void requireGroupExists(Long groupId) {
        if (!communityGroupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group not found: " + groupId);
        }
    }
}
