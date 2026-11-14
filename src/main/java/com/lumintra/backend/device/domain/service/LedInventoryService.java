package com.lumintra.backend.device.domain.service;

import java.util.Map;

/**
 * Domain boundary exposed by Device for atomic LED hardware inventory management during order lifecycle events.
 */
public interface LedInventoryService {

    /**
     * Atomically deducts required stock quantities for all specified LED model IDs.
     *
     * @param requiredQuantitiesByModelId map of LED modelId to positive quantity to deduct
     * @return true if every requested LED model had sufficient stock and was deducted; false otherwise
     */
    boolean deductStocksIfAvailable(Map<Long, Long> requiredQuantitiesByModelId);

    /**
     * Atomically restores stock quantities for all specified LED model IDs (e.g., on order cancellation).
     *
     * @param quantitiesByModelId map of LED modelId to positive quantity to restore
     */
    void restoreStocks(Map<Long, Long> quantitiesByModelId);
}
