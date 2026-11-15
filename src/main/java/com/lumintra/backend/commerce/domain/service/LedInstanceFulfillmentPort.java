package com.lumintra.backend.commerce.domain.service;

import com.lumintra.backend.commerce.domain.model.FulfillmentDeviceSnapshot;

/**
 * Domain port for verifying LED hardware units and transitioning their inventory status
 * during order packaging and fulfillment.
 */
public interface LedInstanceFulfillmentPort {

    /**
     * Resolves the physical LED instance by serial number, validates that
     * it exists, is currently IN_STOCK, and matches the expected hardware model ID.
     * Transitions its status from IN_STOCK to IN_TRANSIT atomically without binding
     * to a customer account or activating warranty.
     *
     * @param serialNumber hardware serial number
     * @param expectedModelId expected LED model ID from the order line item
     * @return snapshot of the resolved and transitioned LED instance
     */
    FulfillmentDeviceSnapshot linkAndMarkInTransit(
            String serialNumber,
            Long expectedModelId
    );

    /**
     * Reverts an LED hardware unit from IN_TRANSIT back to IN_STOCK (e.g. upon unlinking/cancellation).
     *
     * @param instanceId hardware instance identifier
     */
    void releaseToStock(Long instanceId);
}
