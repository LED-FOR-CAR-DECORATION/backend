package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import java.math.BigDecimal;
import java.time.Instant;

public record RevenueSourceRow(
        String sourceType,
        Long referenceId,
        String customerName,
        String customerEmail,
        String status,
        String orderType,
        Long productId,
        String itemName,
        String productType,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal contributionAmount,
        BigDecimal orderTotalAmount,
        Instant createdAt
) {}
