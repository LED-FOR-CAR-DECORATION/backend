package com.lumintra.backend.character.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "character")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "character_id")
    private Long characterId;

    @Column(name = "creator_employee_id")
    private Long creatorEmployeeId;

    @Column(name = "character_name", nullable = false, length = 255)
    private String characterName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static CharacterEntity create(Long creatorEmployeeId, String characterName, String description) {
        CharacterEntity entity = new CharacterEntity();
        entity.setCreatorEmployeeId(creatorEmployeeId);
        entity.setCharacterName(characterName);
        entity.setDescription(description);
        return entity;
    }
}
