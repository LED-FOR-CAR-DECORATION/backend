package com.lumintra.backend.commerce.domain.model;

import java.time.Instant;

public record SepayPaymentInstructions(
        String paymentContent,
        String qrCodeUrl,
        String bankAccount,
        String bankName,
        Instant expiresAt
) {}
