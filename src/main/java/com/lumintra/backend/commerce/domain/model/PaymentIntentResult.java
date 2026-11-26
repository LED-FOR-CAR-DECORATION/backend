package com.lumintra.backend.commerce.domain.model;

public record PaymentIntentResult(
        Long paymentId,
        PaymentGateway gateway,
        PaymentStatus status,
        String transactionCode
) {}
