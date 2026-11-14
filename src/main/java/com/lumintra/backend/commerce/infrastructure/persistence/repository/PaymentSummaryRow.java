package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.domain.model.PaymentStatus;

public record PaymentSummaryRow(
        Long orderId,
        PaymentStatus status
) {}
