package com.lumintra.backend.commerce.infrastructure.integration;

import com.lumintra.backend.commerce.domain.service.ProductAvailabilityPort;
import com.lumintra.backend.store.domain.model.ProductType;
import org.springframework.stereotype.Component;

/**
 * Default adapter for ProductAvailabilityPort while LED_MODEL, OWNED_CHARACTER,
 * and CUSTOMER_SUBSCRIPTION modules/tables are not yet implemented in the backend.
 */
@Component
public class DefaultProductAvailabilityAdapter implements ProductAvailabilityPort {

    @Override
    public boolean hasSufficientLedStock(Long productId, int requestedQuantity) {
        return requestedQuantity > 0;
    }

    @Override
    public boolean isDigitalProductOwned(Long customerId, Long productId, ProductType productType) {
        return false;
    }
}
