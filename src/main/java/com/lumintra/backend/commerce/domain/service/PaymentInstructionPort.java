package com.lumintra.backend.commerce.domain.service;

import com.lumintra.backend.commerce.domain.model.SepayPaymentInstructions;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain port for generating active provider payment instructions (e.g., SePay VietQR).
 */
public interface PaymentInstructionPort {

    SepayPaymentInstructions buildInstructions(Long orderId, String transactionCode, BigDecimal amount, Instant expiresAt);

    default SepayPaymentInstructions buildInstructions(Long orderId, BigDecimal amount, Instant expiresAt) {
        return buildInstructions(orderId, null, amount, expiresAt);
    }
}
