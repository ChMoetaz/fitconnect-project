package com.fitconnect.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Records that a {@link User} has earned an {@link Achievement}, with the date of obtention.
 *
 * <p>Deliberately UNIDIRECTIONAL: this entity is the owning side of both {@code @ManyToOne}
 * associations (it holds the two FK {@code @JoinColumn}s), and neither {@code User} nor
 * {@code Achievement} carries an inverse collection. Per the project convention on bidirectional
 * relations, the owning side is what must be mutated and saved — keeping it unidirectional here
 * means "award a badge" is simply "save a UserAchievement", with no inverse collection to keep in
 * sync and no risk of writing the wrong (mappedBy) side (see resolved bug 4b in CLAUDE.md).
 *
 * <p>{@code achievement} is EAGER (a simple reference entity, same rationale as {@code SportType});
 * {@code user} is LAZY + {@code @JsonIgnore} as a plain back-reference we never serialize.
 */
@Entity
@Table(
        name = "user_achievements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_achievement",
                columnNames = {"user_id", "achievement_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userAchievementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @Column(nullable = false)
    private LocalDate earnedAt;
}
