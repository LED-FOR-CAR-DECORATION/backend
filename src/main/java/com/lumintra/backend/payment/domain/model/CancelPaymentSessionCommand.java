package com.lumintra.backend.payment.domain.model;

public record CancelPaymentSessionCommand(
        String paymentCode, PaymentTargetType targetType, Long targetId, Long customerId) {}
