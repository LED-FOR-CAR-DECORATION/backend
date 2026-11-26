package com.lumintra.backend.iam.domain.model;

import java.math.BigDecimal;

public record UserAddressSnapshot(
        Long addressId,
        Long customerId,
        String recipientName,
        String recipientPhone,
        String fullAddress,
        BigDecimal latitude,
        BigDecimal longitude
) {}
