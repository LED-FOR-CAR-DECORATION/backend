package com.lumintra.backend.commerce.domain.service;

import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartItemAvailabilityService {

    private final ProductAvailabilityPort productAvailabilityPort;

    public AvailabilityCheckResult evaluate(Long customerId, ProductSnapshot product, int requestedQuantity) {
        if (product == null) {
            return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_PRODUCT_UNAVAILABLE);
        }

        if (product.status() == ProductStatus.ARCHIVED) {
            return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_PRODUCT_ARCHIVED);
        }

        if (product.status() != ProductStatus.PUBLISHED) {
            return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_PRODUCT_UNAVAILABLE);
        }

        if (product.productType() == ProductType.LED
                && !productAvailabilityPort.hasSufficientLedStock(product.productId(), requestedQuantity)) {
            return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_OUT_OF_STOCK);
        }

        if ((product.productType() == ProductType.CHARACTER || product.productType() == ProductType.SUBSCRIPTION)
                && productAvailabilityPort.isDigitalProductOwned(customerId, product.productId(), product.productType())) {
            return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_ALREADY_OWNED);
        }

        return AvailabilityCheckResult.availableResult();
    }
}
