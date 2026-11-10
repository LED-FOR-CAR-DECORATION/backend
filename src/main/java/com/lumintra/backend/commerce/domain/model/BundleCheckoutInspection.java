package com.lumintra.backend.commerce.domain.model;

import java.util.Map;

public record BundleCheckoutInspection(
        boolean available,
        String unavailableReason,
        boolean containsPhysicalLed,
        Map<Long, Long> requiredLedStocksByModelId
) {
    public static BundleCheckoutInspection unavailable(String reason) {
        return new BundleCheckoutInspection(false, reason, false, Map.of());
    }

    public static BundleCheckoutInspection valid(boolean containsPhysicalLed, Map<Long, Long> requiredLedStocksByModelId) {
        return new BundleCheckoutInspection(true, null, containsPhysicalLed, requiredLedStocksByModelId);
    }
}
