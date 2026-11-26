package com.lumintra.backend.store.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumintra.backend.store.domain.model.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ManagedSubscriptionResponse(
        Long subscriptionDetailId,
        Long productId,
        String planCode,
        String planName,
        BigDecimal basePrice,
        @JsonProperty("isLifetime") boolean lifetime,
        Integer durationDays,
        boolean allAccessCharacters,
        int maxCommissionRevisions,
        int maxMobileProfiles,
        ProductStatus status,
        Instant createdAt) {
    public ManagedSubscriptionResponse(
            Long subscriptionDetailId,
            Long productId,
            String planCode,
            String planName,
            BigDecimal basePrice,
            boolean lifetime,
            Integer durationDays,
            boolean allAccessCharacters,
            int maxCommissionRevisions,
            ProductStatus status,
            Instant createdAt) {
        this(
                subscriptionDetailId,
                productId,
                planCode,
                planName,
                basePrice,
                lifetime,
                durationDays,
                allAccessCharacters,
                maxCommissionRevisions,
                3,
                status,
                createdAt);
    }
}
