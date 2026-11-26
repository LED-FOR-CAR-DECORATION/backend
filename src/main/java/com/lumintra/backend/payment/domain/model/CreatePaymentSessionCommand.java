package com.lumintra.backend.payment.domain.model;

import java.math.BigDecimal;
import java.time.Duration;

public record CreatePaymentSessionCommand(
        PaymentTargetType targetType,
        Long targetId,
        Long customerId,
        BigDecimal amount,
        Duration ttl
) {}
