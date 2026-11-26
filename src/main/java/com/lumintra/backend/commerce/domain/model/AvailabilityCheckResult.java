package com.lumintra.backend.commerce.domain.model;

public record AvailabilityCheckResult(
        boolean available,
        String unavailableReason
) {
    public static final String REASON_PRODUCT_UNAVAILABLE = "PRODUCT_UNAVAILABLE";
    public static final String REASON_PRODUCT_ARCHIVED = "PRODUCT_ARCHIVED";
    public static final String REASON_OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String REASON_ALREADY_OWNED = "ALREADY_OWNED";

    public static AvailabilityCheckResult availableResult() {
        return new AvailabilityCheckResult(true, null);
    }

    public static AvailabilityCheckResult unavailable(String reason) {
        return new AvailabilityCheckResult(false, reason);
    }
}
