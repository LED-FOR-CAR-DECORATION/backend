package com.lumintra.backend.store.domain.model;

import java.math.BigDecimal;

public record ProductSnapshot(
        Long productId,
        String productName,
        ProductType productType,
        String thumbnailUrl,
        BigDecimal basePrice,
        ProductStatus status
) {}
