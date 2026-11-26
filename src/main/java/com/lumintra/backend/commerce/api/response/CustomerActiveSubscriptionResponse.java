package com.lumintra.backend.commerce.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Customer active subscription plan entitlement details")
public record CustomerActiveSubscriptionResponse(
        @Schema(description = "Customer subscription identifier", example = "12")
        Long subscriptionId,

        @Schema(description = "Store product identifier of the subscription plan", example = "16")
        Long productId,

        @Schema(description = "Order identifier that granted the subscription (if purchased via order)", example = "42")
        Long orderId,

        @Schema(description = "Canonical subscription plan code", example = "VIP_LIFETIME")
        String planCode,

        @Schema(description = "Display name of the subscription plan", example = "Lumintra All-Access VIP Pass")
        String planName,

        @Schema(description = "Whether the subscription is valid for lifetime", example = "true")
        boolean isLifetime,

        @Schema(description = "Duration in days for non-lifetime plans", example = "30")
        Integer durationDays,

        @Schema(description = "Whether this plan unlocks all published store characters", example = "true")
        boolean allAccessCharacters,

        @Schema(description = "Complimentary commission revisions granted per commission request", example = "5")
        int maxCommissionRevisions,

        @Schema(description = "Legacy compatibility field matching maxCommissionRevisions", example = "5")
        int remainingCommissionRevisions,

        @Schema(description = "Maximum mobile device profiles allowed", example = "10")
        int maxMobileProfiles,

        @Schema(description = "Activation timestamp", example = "2026-11-24T06:00:00Z")
        Instant activatedAt,

        @Schema(description = "Expiration timestamp (null for lifetime plans)")
        Instant expiresAt,

        @Schema(description = "Purchase price paid for this subscription (or base price if granted without order)", example = "999999.00")
        BigDecimal paidPrice
) {
    public CustomerActiveSubscriptionResponse(
            Long subscriptionId,
            Long productId,
            Long orderId,
            String planCode,
            String planName,
            boolean isLifetime,
            Integer durationDays,
            boolean allAccessCharacters,
            int maxCommissionRevisions,
            int remainingCommissionRevisions,
            int maxMobileProfiles,
            Instant activatedAt,
            Instant expiresAt) {
        this(
                subscriptionId,
                productId,
                orderId,
                planCode,
                planName,
                isLifetime,
                durationDays,
                allAccessCharacters,
                maxCommissionRevisions,
                remainingCommissionRevisions,
                maxMobileProfiles,
                activatedAt,
                expiresAt,
                null);
    }

    public CustomerActiveSubscriptionResponse(
            Long subscriptionId,
            Long productId,
            Long orderId,
            String planCode,
            String planName,
            boolean isLifetime,
            Integer durationDays,
            boolean allAccessCharacters,
            int maxCommissionRevisions,
            int maxMobileProfiles,
            Instant activatedAt,
            Instant expiresAt) {
        this(
                subscriptionId,
                productId,
                orderId,
                planCode,
                planName,
                isLifetime,
                durationDays,
                allAccessCharacters,
                maxCommissionRevisions,
                maxCommissionRevisions,
                maxMobileProfiles,
                activatedAt,
                expiresAt,
                null);
    }
}
