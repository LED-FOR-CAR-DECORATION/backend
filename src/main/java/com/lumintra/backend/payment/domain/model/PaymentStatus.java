package com.lumintra.backend.payment.domain.model;

import java.util.Locale;

public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    EXPIRED,
    CANCELLED,
    FAILED,
    REFUNDED;

    public static PaymentStatus fromDatabaseValue(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return PENDING;
        }
        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SUCCESS", "SUCCEEDED", "PAID", "COMPLETED" -> SUCCEEDED;
            case "EXPIRED" -> EXPIRED;
            case "CANCELLED", "CANCELED" -> CANCELLED;
            case "FAILED" -> FAILED;
            case "REFUNDED" -> REFUNDED;
            default -> PENDING;
        };
    }
}
