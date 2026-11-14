package com.lumintra.backend.payment.domain.service;

import java.time.Duration;

/**
 * Idempotency store tracking processed external SePay webhook transaction IDs (sepay_tx_id).
 */
public interface PaymentWebhookIdempotencyStore {

    boolean isProcessed(Long sepayTxId);

    void markProcessed(Long sepayTxId, String paymentCode, Duration ttl);
}
