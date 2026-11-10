package com.lumintra.backend.commerce.domain.service;

import com.lumintra.backend.store.domain.model.ProductType;

/**
 * Domain port for checking inventory stock (LED_MODEL) and digital ownership
 * (OWNED_CHARACTER / CUSTOMER_SUBSCRIPTION) without coupling commerce to unimplemented
 * persistence tables or external feature repositories.
 */
public interface ProductAvailabilityPort {

    boolean hasSufficientLedStock(Long productId, int requestedQuantity);

    boolean isDigitalProductOwned(Long customerId, Long productId, ProductType productType);
}
