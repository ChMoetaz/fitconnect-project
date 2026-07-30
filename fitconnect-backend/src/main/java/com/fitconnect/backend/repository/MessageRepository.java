package com.fitconnect.backend.repository;

import com.fitconnect.backend.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * The most recent messages of a group, newest first (capped at 50 by the {@code Top50} keyword),
     * so re-opening a busy chat never loads its entire history. {@code sender} is EAGER on the entity
     * (see {@link Message}), so no {@code JOIN FETCH} is needed here — the derived query is enough and
     * the sender is available when {@code MessageService} maps to the DTO inside its transaction.
     * The service reverses the list to chronological (oldest first) for display.
     */
    List<Message> findTop50ByCommunityGroup_CommunityIdOrderBySentAtDesc(Long communityId);

    /** Bulk-remove all messages sent by a user — used by admin user deletion (FK sender_id, no cascade). */
    void deleteBySender_UserId(Long senderId);
}
