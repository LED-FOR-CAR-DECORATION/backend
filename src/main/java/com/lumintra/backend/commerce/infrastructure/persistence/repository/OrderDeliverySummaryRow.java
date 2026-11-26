package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.domain.model.DeliveryStatus;

public record OrderDeliverySummaryRow(
        Long orderId,
        DeliveryStatus deliveryStatus,
        String recipientName,
        String recipientPhone
) {}
