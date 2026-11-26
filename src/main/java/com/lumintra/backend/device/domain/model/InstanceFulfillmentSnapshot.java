package com.lumintra.backend.device.domain.model;

/**
 * Read-only snapshot linking an LED instance to its fulfilled order item and parent order state.
 */
public record InstanceFulfillmentSnapshot(
        Long instanceId,
        Long orderId,
        Long orderItemId,
        Long orderCustomerId,
        String orderStatus
) {
    public InstanceFulfillmentSnapshot(Long instanceId, Long orderId, Long orderItemId) {
        this(instanceId, orderId, orderItemId, null, null);
    }

    public boolean isOrderDeliveredOrCompleted() {
        return "DELIVERED".equals(orderStatus) || "COMPLETED".equals(orderStatus);
    }
}
