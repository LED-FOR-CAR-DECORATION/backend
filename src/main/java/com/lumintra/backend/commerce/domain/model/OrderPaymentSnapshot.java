package com.lumintra.backend.commerce.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only snapshot of order ownership and payment expiration metadata exposed to the payment module.
 */
public record OrderPaymentSnapshot(
        Long orderId,
        Long customerId,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant expiresAt
) {}
