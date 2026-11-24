package com.lumintra.backend.commerce.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

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

        @Schema(description = "Maximum complimentary commission revisions included", example = "3")
        int maxCommissionRevisions,

        @Schema(description = "Maximum mobile device profiles allowed", example = "10")
        int maxMobileProfiles,

        @Schema(description = "Activation timestamp", example = "2026-11-24T06:00:00Z")
        Instant activatedAt,

        @Schema(description = "Expiration timestamp (null for lifetime plans)")
        Instant expiresAt
) {}
