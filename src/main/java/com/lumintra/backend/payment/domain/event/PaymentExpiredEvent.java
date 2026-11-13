package com.lumintra.backend.payment.domain.event;

import com.lumintra.backend.payment.domain.model.PaymentTargetType;

import java.time.Instant;

/**
 * Domain event published when a pending SePay payment session expires before payment completion.
 */
public record PaymentExpiredEvent(
        Long transactionId,
        String paymentCode,
        PaymentTargetType targetType,
        Long targetId,
        Instant expiredAt
) {
    public PaymentExpiredEvent(Long transactionId, PaymentTargetType targetType, Long targetId) {
        this(transactionId, null, targetType, targetId, Instant.now());
    }

    public Long paymentId() {
        return transactionId;
    }
}
