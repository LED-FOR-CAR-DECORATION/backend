package com.lumintra.backend.payment.domain.model;

import java.time.Instant;

public record PaymentSessionMetadata(
        String paymentCode,
        PaymentTargetType targetType,
        Long targetId,
        Long customerId,
        Instant expiresAt
) {}
