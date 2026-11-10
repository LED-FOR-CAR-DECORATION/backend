package com.lumintra.backend.device.domain.service;

import java.util.Map;

/**
 * Domain boundary exposed by Device for atomic LED hardware inventory deduction during order checkout.
 */
public interface LedInventoryService {

    /**
     * Atomically deducts required stock quantities for all specified LED model IDs.
     *
     * @param requiredQuantitiesByModelId map of LED modelId to positive quantity to deduct
     * @return true if every requested LED model had sufficient stock and was deducted; false otherwise
     */
    boolean deductStocksIfAvailable(Map<Long, Long> requiredQuantitiesByModelId);
}
