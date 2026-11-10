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
@Table(name = "customer_subscription")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "is_lifetime", nullable = false)
    private boolean isLifetime = true;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @CreationTimestamp
    @Column(name = "activated_at", nullable = false, updatable = false)
    private Instant activatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static CustomerSubscriptionEntity createActive(Long customerId, Long productId, Long orderItemId) {
        CustomerSubscriptionEntity entity = new CustomerSubscriptionEntity();
        entity.setCustomerId(customerId);
        entity.setProductId(productId);
        entity.setOrderItemId(orderItemId);
        entity.setLifetime(true);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
