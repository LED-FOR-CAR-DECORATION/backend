package com.lumintra.backend.commerce.infrastructure.persistence.entity;

import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "\"order\"")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "delivery_address_id")
    private Long deliveryAddressId;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OrderEntity createPendingPayment(
            Long customerId,
            Long deliveryAddressId,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal totalAmount,
            OrderType orderType,
            Instant expiresAt) {
        OrderEntity entity = new OrderEntity();
        entity.setCustomerId(customerId);
        entity.setDeliveryAddressId(deliveryAddressId);
        entity.setSubtotal(subtotal);
        entity.setShippingFee(shippingFee);
        entity.setTotalAmount(totalAmount);
        entity.setOrderType(orderType);
        entity.setStatus(OrderStatus.PENDING_PAYMENT);
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    public static OrderEntity of(
            Long orderId,
            Long customerId,
            Long deliveryAddressId,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal totalAmount,
            OrderType orderType,
            OrderStatus status,
            Instant expiresAt) {
        OrderEntity entity = createPendingPayment(
                customerId,
                deliveryAddressId,
                subtotal,
                shippingFee,
                totalAmount,
                orderType,
                expiresAt
        );
        entity.setOrderId(orderId);
        entity.setStatus(status);
        return entity;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && Objects.equals(this.customerId, userId);
    }
}
