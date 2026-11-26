package com.lumintra.backend.commerce.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record CarrierDispatchCommand(
        Long orderId,
        String serviceId,
        String remarks,
        BigDecimal weightKg,
        String internalTrackingNumber,
        String recipientName,
        String recipientPhone,
        String recipientAddress,
        BigDecimal recipientLatitude,
        BigDecimal recipientLongitude,
        List<DispatchItem> items
) {
    public record DispatchItem(
            Long productId,
            String name,
            int quantity,
            BigDecimal subtotal
    ) {}
}
