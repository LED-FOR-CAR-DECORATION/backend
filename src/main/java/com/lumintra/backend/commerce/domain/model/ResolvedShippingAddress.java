package com.lumintra.backend.commerce.domain.model;

import java.math.BigDecimal;

public record ResolvedShippingAddress(
        Long deliveryAddressId,
        String recipientName,
        String recipientPhone,
        String fullAddress,
        BigDecimal latitude,
        BigDecimal longitude
) {}
