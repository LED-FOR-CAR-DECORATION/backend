package com.lumintra.backend.commission.infrastructure.persistence.entity;

import com.lumintra.backend.commission.domain.model.ModifierType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "commission_price_option", indexes = {
    @Index(name = "idx_comm_option_config", columnList = "config_id"),
    @Index(name = "idx_comm_option_active_order", columnList = "is_active, display_order")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommissionPriceOptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "config_id", nullable = false)
    private CommissionPriceConfigEntity config;

    @Column(name = "option_code", nullable = false, length = 100)
    private String optionCode;

    @Column(name = "option_label", nullable = false, length = 255)
    private String optionLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "modifier_type", nullable = false, length = 30)
    @ColumnDefault("'FLAT_ADD'")
    @Builder.Default
    private ModifierType modifierType = ModifierType.FLAT_ADD;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

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

    public static CommissionPriceOptionEntity create(
            String optionCode,
            String optionLabel,
            ModifierType modifierType,
            BigDecimal basePrice,
            Boolean isDefault,
            Integer displayOrder) {
        return CommissionPriceOptionEntity.builder()
                .optionCode(optionCode != null ? optionCode.trim() : "")
                .optionLabel(optionLabel != null ? optionLabel.trim() : "")
                .modifierType(modifierType != null ? modifierType : ModifierType.FLAT_ADD)
                .basePrice(basePrice != null ? basePrice : BigDecimal.ZERO)
                .isDefault(Boolean.TRUE.equals(isDefault))
                .isActive(true)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .build();
    }

    public ModifierType resolvedModifierType() {
        return this.modifierType != null ? this.modifierType : ModifierType.FLAT_ADD;
    }

    // ERD Compatibility Aliases
    public String getOptionValue() { return this.optionCode; }
    public void setOptionValue(String optionValue) { this.optionCode = optionValue; }
    public String getOptionName() { return this.optionLabel; }
    public void setOptionName(String optionName) { this.optionLabel = optionName; }
    public BigDecimal getOptionPrice() { return this.basePrice; }
    public void setOptionPrice(BigDecimal optionPrice) { this.basePrice = optionPrice; }
}
