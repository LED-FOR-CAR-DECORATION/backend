package com.lumintra.backend.store.api.response;

import java.math.BigDecimal;

public record ManagedBundleResponse(
        Long bundleId,
        Long productId,
        String bundleName,
        String description,
        BigDecimal originalPrice,
        BigDecimal bundlePrice,
        String status,
        long itemCount) {}
