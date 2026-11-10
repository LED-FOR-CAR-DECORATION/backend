package com.lumintra.backend.commerce.domain.event;

import com.lumintra.backend.commerce.domain.model.PaymentGateway;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedEvent(
        Long orderId,
        Long customerId,
        Long paymentId,
        PaymentGateway paymentGateway,
        BigDecimal totalAmount,
        Instant expiresAt
) {}
