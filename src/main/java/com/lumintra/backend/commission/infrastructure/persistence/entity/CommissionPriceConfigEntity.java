package com.lumintra.backend.commission.infrastructure.persistence.entity;

import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "commission_price_config", indexes = {
    @Index(name = "idx_comm_config_rule_key", columnList = "rule_key", unique = true),
    @Index(name = "idx_comm_config_active_order", columnList = "is_active, display_order")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommissionPriceConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "rule_key", nullable = false, unique = true, length = 100)
    private String ruleKey;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false, length = 50)
    private SelectionType selectionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ui_type", nullable = false, length = 50)
    private UiType uiType;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean isRequired = false;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean isSystem = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "depends_on_option_id")
    private Long dependsOnOptionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<CommissionPriceOptionEntity> options = new ArrayList<>();

    public static CommissionPriceConfigEntity createCustom(
            String ruleKey,
            String name,
            String description,
            SelectionType selectionType,
            UiType uiType,
            Boolean isRequired,
            Integer displayOrder,
            Long dependsOnOptionId,
            List<CommissionPriceOptionEntity> optionEntities) {
        CommissionPriceConfigEntity config = CommissionPriceConfigEntity.builder()
                .ruleKey(ruleKey != null ? ruleKey.trim() : "")
                .name(name != null ? name.trim() : "")
                .description(description)
                .selectionType(selectionType)
                .uiType(uiType)
                .isRequired(Boolean.TRUE.equals(isRequired))
                .isSystem(false)
                .isActive(true)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .dependsOnOptionId(dependsOnOptionId)
                .build();

        if (optionEntities != null) {
            for (CommissionPriceOptionEntity option : optionEntities) {
                config.addOption(option);
            }
        }
        return config;
    }

    // ERD Compatibility Aliases
    public String getConfigCode() { return this.ruleKey; }
    public void setConfigCode(String configCode) { this.ruleKey = configCode; }
    public String getConfigName() { return this.name; }
    public void setConfigName(String configName) { this.name = configName; }

    public void addOption(CommissionPriceOptionEntity option) {
        if (options == null) {
            options = new ArrayList<>();
        }
        options.add(option);
        option.setConfig(this);
    }
}
