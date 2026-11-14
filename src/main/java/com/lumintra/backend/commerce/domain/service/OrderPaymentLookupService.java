package com.lumintra.backend.commerce.domain.service;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;

import java.util.Optional;

/**
 * Domain service port exposing read-only order ownership and payment window metadata to other features.
 */
public interface OrderPaymentLookupService {

    Optional<OrderPaymentSnapshot> findByOrderId(Long orderId);
}
