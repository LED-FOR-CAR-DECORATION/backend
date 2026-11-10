package com.lumintra.backend.commerce.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "owned_character")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnedCharacterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "owned_id")
    private Long ownedId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "is_revoked", nullable = false)
    private boolean isRevoked = false;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static OwnedCharacterEntity create(Long customerId, Long versionId, Long orderItemId) {
        OwnedCharacterEntity entity = new OwnedCharacterEntity();
        entity.setCustomerId(customerId);
        entity.setVersionId(versionId);
        entity.setOrderItemId(orderItemId);
        entity.setRevoked(false);
        return entity;
    }
}
