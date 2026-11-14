package com.lumintra.backend.commerce.infrastructure.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "app.ahamove")
public record AhamoveProperties(
        String baseUrl,
        String apiKey,
        String mobile,
        String paymentMethod,
        Pickup pickup,
        Webhook webhook
) {
    private static final String DEFAULT_BASE_URL = "https://partner-apistg.ahamove.com/v3";
    private static final String DEFAULT_PAYMENT_METHOD = "BALANCE";
    private static final String DEFAULT_WEBHOOK_HEADER_NAME = "X-Api-Key";
    private static final Set<String> SUPPORTED_PAYMENT_METHODS = Set.of("BALANCE", "CASH", "CASH_BY_RECIPIENT");

    @ConstructorBinding
    public AhamoveProperties {
    }

    public AhamoveProperties(
            String baseUrl,
            String apiKey,
            String mobile,
            String paymentMethod,
            Pickup pickup) {
        this(baseUrl, apiKey, mobile, paymentMethod, pickup, null);
    }

    public record Webhook(
            Boolean requireAuth,
            String headerName,
            String token
    ) {
        @ConstructorBinding
        public Webhook {
        }

        public Webhook(String headerName, String token) {
            this(true, headerName, token);
        }
    }

    public boolean isWebhookAuthRequired() {
        return webhook == null || webhook.requireAuth() == null || webhook.requireAuth();
    }

    public String normalizedWebhookHeaderName() {
        if (webhook == null || webhook.headerName() == null || webhook.headerName().isBlank()) {
            return DEFAULT_WEBHOOK_HEADER_NAME;
        }
        return webhook.headerName().trim();
    }

    public String resolvedWebhookToken() {
        if (webhook == null || webhook.token() == null || webhook.token().isBlank()) {
            return null;
        }
        return webhook.token().trim();
    }

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
