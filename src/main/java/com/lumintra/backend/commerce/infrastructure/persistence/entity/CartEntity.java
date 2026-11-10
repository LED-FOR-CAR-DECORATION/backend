package com.lumintra.backend.commerce.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "cart")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "customer_id", nullable = false, unique = true)
    private Long customerId;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CartEntity create(Long customerId) {
        CartEntity entity = new CartEntity();
        entity.setCustomerId(customerId);
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    public static CartEntity of(Long cartId, Long customerId, Instant updatedAt) {
        CartEntity entity = new CartEntity();
        entity.setCartId(cartId);
        entity.setCustomerId(customerId);
        entity.setUpdatedAt(updatedAt);
        return entity;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && Objects.equals(this.customerId, userId);
    }
}
