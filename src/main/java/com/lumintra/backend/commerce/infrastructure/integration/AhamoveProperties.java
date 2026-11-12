package com.lumintra.backend.commerce.infrastructure.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "app.ahamove")
public record AhamoveProperties(
        String baseUrl,
        String apiKey,
        String mobile,
        String paymentMethod,
        Pickup pickup
) {
    private static final String DEFAULT_BASE_URL = "https://partner-apistg.ahamove.com/v3";
    private static final String DEFAULT_PAYMENT_METHOD = "BALANCE";
    private static final Set<String> SUPPORTED_PAYMENT_METHODS = Set.of("BALANCE", "CASH", "CASH_BY_RECIPIENT");

    public record Pickup(
            String name,
            String mobile,
            String address,
            BigDecimal lat,
            BigDecimal lng
    ) {}

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public String normalizedPaymentMethod() {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return DEFAULT_PAYMENT_METHOD;
        }
        return paymentMethod.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isPaymentMethodValid() {
        return SUPPORTED_PAYMENT_METHODS.contains(normalizedPaymentMethod());
    }

    public String resolvedPickupMobile() {
        if (pickup != null && pickup.mobile() != null && !pickup.mobile().isBlank()) {
            return pickup.mobile().trim();
        }
        return mobile != null ? mobile.trim() : null;
    }

    public String resolvedPickupName() {
        if (pickup != null && pickup.name() != null && !pickup.name().isBlank()) {
            return pickup.name().trim();
        }
        return "Lumintra Warehouse";
    }
}
