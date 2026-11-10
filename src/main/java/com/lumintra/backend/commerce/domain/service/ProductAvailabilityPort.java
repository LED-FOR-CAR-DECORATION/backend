package com.lumintra.backend.commerce.domain.service;

import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.domain.model.BundleCheckoutInspection;
import com.lumintra.backend.store.domain.model.ProductType;

/**
 * Domain port for checking hardware stock (LED_MODEL), bundle availability/stock (BUNDLE),
 * and digital ownership (OWNED_CHARACTER / CUSTOMER_SUBSCRIPTION).
 */
public interface ProductAvailabilityPort {

    boolean hasSufficientLedStock(Long productId, int requestedQuantity);

    boolean isDigitalProductOwned(Long customerId, Long productId, ProductType productType);

    AvailabilityCheckResult evaluateBundleAvailability(Long customerId, Long productId, int requestedQuantity);

    BundleCheckoutInspection inspectBundleForCheckout(Long customerId, Long productId, int requestedQuantity);
}
