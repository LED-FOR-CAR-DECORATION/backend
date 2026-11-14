package com.lumintra.backend.device.api.response;

public record RegisteredDeviceResponse(
        Long instanceId, Long modelId, String serialNumber, String status) {}
