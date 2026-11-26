package com.lumintra.backend.payment.domain.service;

import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;

import java.time.Duration;
import java.util.Optional;

/**
 * Port for storing and retrieving session ownership and expiration metadata
 * that is not present as dedicated columns on the finalized public.payment_transaction table.
 */
public interface PaymentSessionMetadataStore {

    void save(PaymentSessionMetadata metadata, Duration ttl);

    Optional<PaymentSessionMetadata> findByPaymentCode(String paymentCode);
}
