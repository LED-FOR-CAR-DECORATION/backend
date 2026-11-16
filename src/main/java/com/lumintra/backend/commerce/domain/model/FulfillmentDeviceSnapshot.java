package com.lumintra.backend.commerce.domain.model;

public record FulfillmentDeviceSnapshot(
        Long instanceId,
        Long modelId,
        String modelName,
        String serialNumber,
        String macAddress,
        String status
) {}
