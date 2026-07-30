package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.CommunityGroup;
import com.fitconnect.backend.domain.Message;
import com.fitconnect.backend.domain.User;
import com.fitconnect.backend.dto.MessageResponse;
import com.fitconnect.backend.exception.BadRequestException;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.CommunityGroupRepository;
import com.fitconnect.backend.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MessageService} — repositories/UserService mocked, no database and no
 * WebSocket. Covers persistence + DTO mapping (happy path), the content validation branches
 * (blank / too long), the group-membership guard (you cannot post in a group you have not joined),
 * missing group/sender, and that the history is returned oldest-first.
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private CommunityGroupRepository communityGroupRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private MessageService messageService;

    private static final Long GROUP_ID = 7L;
    private static final Long USER_ID = 3L;

    private CommunityGroup group() {
        return CommunityGroup.builder().communityId(GROUP_ID).name("Berlin Runners").build();
    }

    /** A user who is a member of GROUP_ID (owning-side communityGroups contains the group). */
    private User memberUser() {
        Set<CommunityGroup> groups = new HashSet<>();
        groups.add(group());
        return User.builder()
                .userId(USER_ID).email("runner@fitconnect.test").role("USER")
                .communityGroups(groups)
                .build();
    }

    private User nonMemberUser() {
        return User.builder()
                .userId(USER_ID).email("stranger@fitconnect.test").role("USER")
                .communityGroups(new HashSet<>())
                .build();
    }

    @Test
    void saveMessage_persistsAndMapsToDto_forMember() {
        when(communityGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(userService.getById(USER_ID)).thenReturn(memberUser());
        // save() returns the entity with an id assigned, like the real IDENTITY generator.
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setMessageId(100L);
            return m;
        });

        MessageResponse response = messageService.saveMessage(GROUP_ID, USER_ID, "  Let's run at 7  ");

        assertThat(response.getMessageId()).isEqualTo(100L);
        assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(response.getSenderId()).isEqualTo(USER_ID);
        assertThat(response.getSenderEmail()).isEqualTo("runner@fitconnect.test");
        assertThat(response.getContent()).isEqualTo("Let's run at 7"); // trimmed
        assertThat(response.getSentAt()).isNotNull();
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void saveMessage_rejectsBlankContent() {
        assertThatThrownBy(() -> messageService.saveMessage(GROUP_ID, USER_ID, "   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be empty");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void saveMessage_rejectsNullContent() {
        assertThatThrownBy(() -> messageService.saveMessage(GROUP_ID, USER_ID, null))
                .isInstanceOf(BadRequestException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void saveMessage_rejectsTooLongContent() {
        String tooLong = "x".repeat(Message.MAX_CONTENT_LENGTH + 1);

        assertThatThrownBy(() -> messageService.saveMessage(GROUP_ID, USER_ID, tooLong))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not exceed");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void saveMessage_rejectsNonMember() {
        when(communityGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(userService.getById(USER_ID)).thenReturn(nonMemberUser());

        assertThatThrownBy(() -> messageService.saveMessage(GROUP_ID, USER_ID, "hello"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("join the group");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void saveMessage_throwsWhenGroupMissing() {
        when(communityGroupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.saveMessage(GROUP_ID, USER_ID, "hello"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Group not found");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void getHistory_throwsWhenGroupMissing() {
        when(communityGroupRepository.existsById(GROUP_ID)).thenReturn(false);

        assertThatThrownBy(() -> messageService.getHistory(GROUP_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(messageRepository, never())
                .findTop50ByCommunityGroup_CommunityIdOrderBySentAtDesc(any());
    }

    @Test
    void getHistory_rejectsNonMemberWithAccessDenied() {
        when(communityGroupRepository.existsById(GROUP_ID)).thenReturn(true);
        when(userService.getById(USER_ID)).thenReturn(nonMemberUser());

        // Same membership rule as the send side, but a READ by a non-member is a 403 (AccessDenied),
        // not a 400 — GlobalExceptionHandler / RestAccessDeniedHandler turn it into a clean 403.
        assertThatThrownBy(() -> messageService.getHistory(GROUP_ID, USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("member of the group");
        verify(messageRepository, never())
                .findTop50ByCommunityGroup_CommunityIdOrderBySentAtDesc(any());
    }

    @Test
    void getHistory_returnsChronologicalOldestFirst() {
        when(communityGroupRepository.existsById(GROUP_ID)).thenReturn(true);
        User sender = memberUser();
        when(userService.getById(USER_ID)).thenReturn(sender);
        Message older = Message.builder()
                .messageId(1L).content("first").sentAt(Instant.parse("2026-07-29T10:00:00Z"))
                .sender(sender).communityGroup(group()).build();
        Message newer = Message.builder()
                .messageId(2L).content("second").sentAt(Instant.parse("2026-07-29T11:00:00Z"))
                .sender(sender).communityGroup(group()).build();
        // Repository returns newest-first (as the Top50...OrderBySentAtDesc query does).
        when(messageRepository.findTop50ByCommunityGroup_CommunityIdOrderBySentAtDesc(GROUP_ID))
                .thenReturn(List.of(newer, older));

        List<MessageResponse> history = messageService.getHistory(GROUP_ID, USER_ID);

        assertThat(history).extracting(MessageResponse::getContent)
                .containsExactly("first", "second"); // reversed to oldest-first
    }
}
