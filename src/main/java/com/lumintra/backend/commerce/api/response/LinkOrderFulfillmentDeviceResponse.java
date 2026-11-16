package com.lumintra.backend.commerce.api.response;

import java.time.Instant;

public record LinkOrderFulfillmentDeviceResponse(
        Long orderId,
        String orderStatus,
        Long fulfillmentId,
        Long orderItemId,
        Long instanceId,
        String serialNumber,
        String macAddress,
        Long modelId,
        String modelName,
        String instanceStatus,
        FulfillmentProgress progress,
        Instant linkedAt
) {
    public record FulfillmentProgress(
            long itemFulfilledCount,
            long itemRequiredCount,
            long orderFulfilledCount,
            long orderRequiredCount,
            boolean isFullyFulfilled
    ) {}
}
