package com.lumintra.backend.character.infrastructure.persistence.entity;

import com.lumintra.backend.character.domain.model.CharacterType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "character",
        indexes = {
                @Index(name = "idx_character_type_commission", columnList = "creator_employee_id, character_type, commission_id")
        }
)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "character_type", nullable = false, length = 20)
    private CharacterType characterType = CharacterType.STORE;

    @Column(name = "commission_id")
    private Long commissionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static CharacterEntity create(Long creatorEmployeeId, String characterName, String description) {
        return create(creatorEmployeeId, characterName, description, CharacterType.STORE, null);
    }

    public static CharacterEntity create(
            Long creatorEmployeeId,
            String characterName,
            String description,
            CharacterType characterType,
            Long commissionId) {
        CharacterType resolvedType = characterType != null ? characterType : CharacterType.STORE;
        if (resolvedType == CharacterType.STORE && commissionId != null) {
            throw new IllegalArgumentException("commissionId is only allowed when characterType is COMMISSION.");
        }
        CharacterEntity entity = new CharacterEntity();
        entity.setCreatorEmployeeId(creatorEmployeeId);
        entity.setCharacterName(characterName);
        entity.setDescription(description);
        entity.setCharacterType(resolvedType);
        entity.setCommissionId(resolvedType == CharacterType.COMMISSION ? commissionId : null);
        return entity;
    }

    public void update(String characterName, String description) {
        this.characterName = characterName;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public boolean isOwnedBy(Long employeeId) {
        return this.creatorEmployeeId != null && this.creatorEmployeeId.equals(employeeId);
    }

    public boolean isStoreCharacter() {
        return this.characterType == null || this.characterType == CharacterType.STORE;
    }

    public boolean isCommissionCharacter() {
        return this.characterType == CharacterType.COMMISSION;
    }

    public void assignCommissionIfUnbound(Long commissionId) {
        if (!isCommissionCharacter()) {
            throw new IllegalStateException("Only COMMISSION characters can be linked to a commission.");
        }
        if (this.commissionId == null && commissionId != null) {
            this.commissionId = commissionId;
        }
    }
}
