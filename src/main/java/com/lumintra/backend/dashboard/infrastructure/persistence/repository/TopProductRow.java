package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import java.math.BigDecimal;

public record TopProductRow(
        Long productId,
        String productName,
        String productType,
        String thumbnailUrl,
        long totalUnits,
        BigDecimal totalRevenue,
        String productStatus,
        Long availableStock,
        String ledModelStatus
) {
    public TopProductRow(
            Long productId,
            String productName,
            String productType,
            String thumbnailUrl,
            long totalUnits,
            BigDecimal totalRevenue) {
        this(productId, productName, productType, thumbnailUrl, totalUnits, totalRevenue, "PUBLISHED", null, null);
    }
}
