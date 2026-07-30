package com.fitconnect.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A single chat message posted by a {@link User} into a {@link CommunityGroup}'s real-time chat
 * (the "In-App Chat" nice-to-have that replaces the static Group feed). Persisted so the history
 * can be reloaded when a user re-opens the chat, before WebSocket takes over for live messages.
 *
 * <p><b>Fetch strategy</b>:
 * <ul>
 *   <li>{@code sender} is {@code @ManyToOne(EAGER)} — same design decision as {@code SportType}
 *       (resolved bug 3): the chat history is almost always rendered <em>with the sender</em>, so
 *       loading it eagerly is not over-fetching, and it removes the LazyInitialization risk at the
 *       source without a dedicated {@code JOIN FETCH} in every query. An EAGER {@code @ManyToOne}
 *       toward {@code User} loads only the user row; its own {@code @JsonIgnore} collections stay
 *       LAZY and are never touched (the message is only ever exposed as {@link
 *       com.fitconnect.backend.dto.MessageResponse}, never as the raw entity).</li>
 *   <li>{@code communityGroup} is a LAZY {@code @JsonIgnore} back-reference, same pattern as
 *       {@code Event.communityGroup} / {@code ProgressRecord.user}: never serialized, only used
 *       server-side to scope the message to its group.</li>
 * </ul>
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    /** Upper bound on a chat message, mirrored by MessageService's validation and MessageResponse. */
    public static final int MAX_CONTENT_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(nullable = false)
    private Instant sentAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_group_id", nullable = false)
    @JsonIgnore
    private CommunityGroup communityGroup;
}
