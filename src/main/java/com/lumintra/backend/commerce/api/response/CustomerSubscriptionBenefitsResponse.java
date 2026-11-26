package com.lumintra.backend.commerce.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Aggregated subscription entitlements, active plans, and history for the authenticated customer")
public record CustomerSubscriptionBenefitsResponse(
        @Schema(description = "Whether the customer currently has at least one active, non-expired subscription", example = "true")
        boolean hasActiveSubscription,

        @Schema(description = "Whether all published store characters are unlocked", example = "true")
        boolean allAccessCharacters,

        @Schema(description = "Default mobile text-profile limit for free accounts", example = "3")
        int defaultMobileProfiles,

        @Schema(description = "Effective maximum mobile text-profile limit currently granted", example = "10")
        int maxMobileProfiles,

        @Schema(description = "Default free commission revisions per commission request", example = "1")
        int defaultCommissionRevisions,

        @Schema(description = "Effective free commission revisions per commission request currently granted", example = "5")
        int maxCommissionRevisions,

        @Schema(description = "Automatic upgrade discount available when upgrading from a Full-Char-Only plan to Ultimate", example = "999999.00")
        BigDecimal upgradeCreditAvailable,

        @Schema(description = "Currently active, non-expired subscription plans owned by the customer")
        List<CustomerActiveSubscriptionResponse> activeSubscriptions,

        @Schema(description = "Expired or inactive historical subscription records owned by the customer")
        List<SubscriptionHistoryItem> subscriptionHistory
) {
    @Schema(description = "Expired or inactive customer subscription record")
    public record SubscriptionHistoryItem(
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
            String status,
            Instant activatedAt,
            Instant expiresAt,
            BigDecimal paidPrice
    ) {}
}
