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
        ProductStatus status,
        Instant createdAt) {}
