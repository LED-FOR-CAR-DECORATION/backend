package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderAuditHeaderRow(
        Long orderId,
        String customerName,
        String customerEmail,
        String status,
        String orderType,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        Instant createdAt,
        String paymentCode,
        String paymentGateway,
        String paymentStatus,
        Instant paidAt
) {}
