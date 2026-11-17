package com.lumintra.backend.store.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumintra.backend.store.domain.model.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreatedSubscriptionResponse(
        Long subscriptionDetailId,
        Long productId,
        String planCode,
        String planName,
        BigDecimal basePrice,
        String description,
        String thumbnailUrl,
        @JsonProperty("isLifetime") boolean lifetime,
        Integer durationDays,
        boolean allAccessCharacters,
        ProductStatus status,
        Instant createdAt,
        List<Long> tagIds) {}
