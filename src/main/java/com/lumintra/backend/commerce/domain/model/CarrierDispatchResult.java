package com.lumintra.backend.commerce.domain.model;

import java.math.BigDecimal;

public record CarrierDispatchResult(
        String trackingNumber,
        String trackingUrl,
        BigDecimal carrierFee,
        String providerStatus
) {}
