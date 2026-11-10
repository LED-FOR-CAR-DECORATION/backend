package com.lumintra.backend.store.infrastructure.persistence.entity;

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
@Table(name = "bundle")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BundleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bundle_id")
    private Long bundleId;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "bundle_name", nullable = false, length = 255)
    private String bundleName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "bundle_price", precision = 12, scale = 2)
    private BigDecimal bundlePrice;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static BundleEntity create(
            Long productId,
            String bundleName,
            String description,
            BigDecimal originalPrice,
            BigDecimal bundlePrice,
            String status,
            Instant startAt,
            Instant endAt) {
        BundleEntity entity = new BundleEntity();
        entity.setProductId(productId);
        entity.setBundleName(bundleName);
        entity.setDescription(description);
        entity.setOriginalPrice(originalPrice);
        entity.setBundlePrice(bundlePrice);
        entity.setStatus(status);
        entity.setStartAt(startAt);
        entity.setEndAt(endAt);
        return entity;
    }
}
