package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import java.math.BigDecimal;

public record LowStockLedRow(
        Long modelId,
        Long productId,
        String modelName,
        Integer widthPx,
        Integer heightPx,
        Long stockQuantity,
        BigDecimal basePrice,
        Long reservedQuantity
) {
    public LowStockLedRow(
            Long modelId,
            Long productId,
            String modelName,
            Integer widthPx,
            Integer heightPx,
            Long stockQuantity,
            BigDecimal basePrice) {
        this(modelId, productId, modelName, widthPx, heightPx, stockQuantity, basePrice, 0L);
    }
}
