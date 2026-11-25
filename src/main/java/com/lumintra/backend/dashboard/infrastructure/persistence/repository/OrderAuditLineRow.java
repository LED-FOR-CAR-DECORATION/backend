package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import java.math.BigDecimal;

public record OrderAuditLineRow(
        Long orderItemId,
        Long productId,
        String productName,
        String productType,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {}
