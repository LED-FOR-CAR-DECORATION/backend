package com.lumintra.backend.device.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Response returned after permanently binding an LED hardware device to the customer account and starting its 180-day warranty")
@Builder
public record ClaimDeviceResponse(
        @Schema(description = "Unique LED hardware device instance identifier", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Long deviceId,

        @Schema(description = "Factory-assigned unique serial number", example = "LMT-32X64-SEED-0003", requiredMode = Schema.RequiredMode.REQUIRED)
        String serialNumber,

        @Schema(description = "Uppercase colon-separated ESP32 MAC address", example = "AA:BB:CC:11:22:03", requiredMode = Schema.RequiredMode.REQUIRED)
        String macAddress,

        @Schema(description = "Assigned display name for the device (custom or model name fallback)", example = "Front Windshield LED", requiredMode = Schema.RequiredMode.REQUIRED)
        String deviceName,

        @Schema(description = "Parent LED hardware model identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
        Long modelId,

        @Schema(description = "Parent LED hardware model display name", example = "Lumintra Matrix 32x64", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String modelName,

        @Schema(description = "Structured technical specifications including canvas dimensions and ordered attributes", requiredMode = Schema.RequiredMode.REQUIRED)
        LedModelListResponse.LedSpec spec,

        @Schema(description = "ISO-8601 UTC timestamp when the device was permanently bound and warranty started", example = "2026-11-14T09:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant boundAt,

        @Schema(description = "ISO-8601 UTC timestamp when the 180-day hardware warranty expires", example = "2027-05-13T09:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant warrantyExpiresAt
) {}
