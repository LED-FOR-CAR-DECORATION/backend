package com.lumintra.backend.payment.domain.event;

import com.lumintra.backend.payment.domain.model.PaymentTargetType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when a SePay payment session is confirmed as paid.
 */
public record PaymentSucceededEvent(
        Long paymentId,
        String paymentCode,
        PaymentTargetType targetType,
        Long targetId,
        Long customerId,
        BigDecimal amount,
        Long sepayTxId,
        String referenceCode,
        Instant paidAt
) {}
