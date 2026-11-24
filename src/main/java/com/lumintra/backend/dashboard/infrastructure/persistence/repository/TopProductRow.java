package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import java.math.BigDecimal;

public record TopProductRow(
        Long productId,
        String productName,
        String productType,
        String thumbnailUrl,
        long totalUnits,
        BigDecimal totalRevenue
) {}
