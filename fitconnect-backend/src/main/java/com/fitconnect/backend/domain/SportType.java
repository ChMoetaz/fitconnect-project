package com.fitconnect.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sport_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SportType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sportTypeId;

    @Column(nullable = false, unique = true)
    private String name;
}
