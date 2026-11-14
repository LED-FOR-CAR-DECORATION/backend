package com.lumintra.backend.commerce.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "order_item_fulfillment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemFulfillmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fulfillment_id")
    private Long fulfillmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItemEntity orderItem;

    @Column(name = "instance_id", nullable = false, unique = true)
    private Long instanceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static OrderItemFulfillmentEntity create(OrderItemEntity orderItem, Long instanceId) {
        Objects.requireNonNull(orderItem, "orderItem must not be null");
        Objects.requireNonNull(instanceId, "instanceId must not be null");

        OrderItemFulfillmentEntity entity = new OrderItemFulfillmentEntity();
        entity.setOrderItem(orderItem);
        entity.setInstanceId(instanceId);
        return entity;
    }
}
