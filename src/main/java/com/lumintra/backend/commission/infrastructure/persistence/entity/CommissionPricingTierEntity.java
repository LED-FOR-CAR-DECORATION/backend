package com.lumintra.backend.commission.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "commission_pricing_tier", indexes = {
    @Index(name = "idx_comm_tier_code", columnList = "tier_code", unique = true),
    @Index(name = "idx_comm_tier_active_order", columnList = "is_active, display_order")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommissionPricingTierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tier_id")
    private Long tierId;

    @Column(name = "tier_code", nullable = false, unique = true, length = 50)
    private String tierCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "extra_revision_fee_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal extraRevisionFeePercent = new BigDecimal("10.00");

    @Column(name = "max_revisions", nullable = false)
    @Builder.Default
    private int maxRevisions = 1;

    @Column(name = "is_custom", nullable = false)
    @Builder.Default
    private boolean isCustom = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "commission_pricing_tier_option",
        joinColumns = @JoinColumn(name = "tier_id"),
        inverseJoinColumns = @JoinColumn(name = "option_id")
    )
    @Builder.Default
    private Set<CommissionPriceOptionEntity> includedOptions = new HashSet<>();

    // Compatibility Aliases
    public String getTierName() { return this.name; }
    public void setTierName(String tierName) { this.name = tierName; }
}
