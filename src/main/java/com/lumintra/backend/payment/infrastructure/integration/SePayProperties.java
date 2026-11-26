package com.lumintra.backend.payment.infrastructure.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Locale;

@ConfigurationProperties(prefix = "app.sepay")
public record SePayProperties(
        String qrBaseUrl,
        String bankName,
        String accountNumber,
        String accountName,
        String paymentPrefix,
        String webhookApiKey
) {
    private static final String DEFAULT_QR_BASE_URL = "https://vietqr.app/img";
    private static final String DEFAULT_BANK_NAME = "MBBank";
    private static final String DEFAULT_ACCOUNT_NUMBER = "0123456789";
    private static final String DEFAULT_ACCOUNT_NAME = "LUMINTRA";
    private static final String DEFAULT_PAYMENT_PREFIX = "LMT";

    @ConstructorBinding
    public SePayProperties {
    }

    public String normalizedQrBaseUrl() {
        if (qrBaseUrl == null || qrBaseUrl.isBlank()) {
            return DEFAULT_QR_BASE_URL;
        }
        String trimmed = qrBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public String normalizedBankName() {
        if (bankName == null || bankName.isBlank()) {
            return DEFAULT_BANK_NAME;
        }
        return bankName.trim();
    }

    public String resolvedAccountNumber() {
        if (accountNumber != null && !accountNumber.isBlank()) {
            return accountNumber.trim();
        }
        return DEFAULT_ACCOUNT_NUMBER;
    }

    public String normalizedAccountName() {
        if (accountName == null || accountName.isBlank()) {
            return DEFAULT_ACCOUNT_NAME;
        }
        return accountName.trim();
    }

    public String normalizedPaymentPrefix() {
        if (paymentPrefix == null || paymentPrefix.isBlank()) {
            return DEFAULT_PAYMENT_PREFIX;
        }
        return paymentPrefix.trim().toUpperCase(Locale.ROOT);
    }

    public String resolvedWebhookApiKey() {
        if (webhookApiKey == null || webhookApiKey.isBlank()) {
            return null;
        }
        return webhookApiKey.trim();
    }
}
