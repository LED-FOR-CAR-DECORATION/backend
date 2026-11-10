package com.lumintra.backend.commerce.infrastructure.persistence.entity;

import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_delivery")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDeliveryEntity {

    public static final String DEFAULT_CARRIER = "AHAMOVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_id")
    private Long deliveryId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private OrderEntity order;

    @Column(name = "carrier", nullable = false, length = 50)
    private String carrier = DEFAULT_CARRIER;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "tracking_url", columnDefinition = "TEXT")
    private String trackingUrl;

    @Column(name = "carrier_fee", precision = 12, scale = 2)
    private BigDecimal carrierFee;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "full_address", nullable = false, length = 500)
    private String fullAddress;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 30)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "estimated_delivery_at")
    private Instant estimatedDeliveryAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OrderDeliveryEntity createPending(OrderEntity order, ResolvedShippingAddress shipping) {
        OrderDeliveryEntity entity = new OrderDeliveryEntity();
        entity.setOrder(order);
        entity.setCarrier(DEFAULT_CARRIER);
        entity.setDeliveryStatus(DeliveryStatus.PENDING);
        entity.setCarrierFee(null);
        entity.setRecipientName(shipping.recipientName());
        entity.setRecipientPhone(shipping.recipientPhone());
        entity.setFullAddress(shipping.fullAddress());
        entity.setLatitude(shipping.latitude());
        entity.setLongitude(shipping.longitude());
        return entity;
    }
}
