package com.lumintra.backend.store.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "subscription_detail")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionDetailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_detail_id")
    private Long subscriptionDetailId;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "plan_code", nullable = false, unique = true, length = 100)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 255)
    private String planName;

    @Column(name = "is_lifetime", nullable = false)
    private boolean isLifetime = true;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "all_access_characters", nullable = false)
    private boolean allAccessCharacters = false;

    @Column(name = "max_commission_revisions", nullable = false)
    private int maxCommissionRevisions = 0;

    @Column(name = "max_mobile_profiles", nullable = false)
    private int maxMobileProfiles = 3;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", nullable = false)
    private Map<String, Object> features = new HashMap<>();

    @Column(name = "perks_description", columnDefinition = "TEXT")
    private String perksDescription;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static SubscriptionDetailEntity create(
            Long productId,
            String planCode,
            String planName,
            boolean isLifetime,
            Integer durationDays,
            boolean allAccessCharacters,
            int maxCommissionRevisions,
            int maxMobileProfiles,
            Map<String, Object> features,
            String perksDescription) {
        SubscriptionDetailEntity entity = new SubscriptionDetailEntity();
        entity.setProductId(productId);
        entity.setPlanCode(planCode);
        entity.setPlanName(planName);
        entity.setLifetime(isLifetime);
        entity.setDurationDays(isLifetime ? null : durationDays);
        entity.setAllAccessCharacters(allAccessCharacters);
        entity.setMaxCommissionRevisions(Math.max(0, maxCommissionRevisions));
        entity.setMaxMobileProfiles(Math.max(0, maxMobileProfiles));
        entity.setFeatures(features != null ? new HashMap<>(features) : new HashMap<>());
        entity.setPerksDescription(perksDescription);
        return entity;
    }

    public static SubscriptionDetailEntity of(
            Long subscriptionDetailId,
            Long productId,
            String planCode,
            String planName,
            boolean isLifetime,
            Integer durationDays,
            boolean allAccessCharacters,
            int maxCommissionRevisions,
            int maxMobileProfiles,
            Map<String, Object> features,
            String perksDescription) {
        SubscriptionDetailEntity entity = create(
                productId,
                planCode,
                planName,
                isLifetime,
                durationDays,
                allAccessCharacters,
                maxCommissionRevisions,
                maxMobileProfiles,
                features,
                perksDescription
        );
        entity.setSubscriptionDetailId(subscriptionDetailId);
        return entity;
    }
}
