package com.lumintra.backend.device.domain.model;

/**
 * Read-only snapshot linking an LED instance to its fulfilled order item.
 */
public record InstanceFulfillmentSnapshot(
        Long instanceId,
        Long orderId,
        Long orderItemId
) {}
