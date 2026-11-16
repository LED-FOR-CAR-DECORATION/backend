package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Full customer-facing hardware profile, linked model specification, warranty coverage, and original order fulfillment reference for an owned LED device")
@Builder
public record CustomerDeviceDetailResponse(
        @Schema(description = "Unique LED hardware device identifier (instance ID)", example = "2001", requiredMode = Schema.RequiredMode.REQUIRED)
        Long deviceId,

        @Schema(description = "Factory-assigned unique serial number", example = "LMT-64X64-SEED-0001", requiredMode = Schema.RequiredMode.REQUIRED)
        String serialNumber,

        @Schema(description = "Hardware BLE MAC address in uppercase colon-separated format", example = "AA:BB:CC:11:22:01", requiredMode = Schema.RequiredMode.REQUIRED)
        String macAddress,

        @Schema(description = "Customer-assigned device display name", example = "Rear Window Matrix", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String deviceName,

        @Schema(description = "Current lifecycle status of the hardware device", example = "BOUND", requiredMode = Schema.RequiredMode.REQUIRED)
        LedInstanceStatus status,

        @Schema(description = "Currently installed firmware version identifier reported for the device", example = "12", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long currentFirmwareId,

        @Schema(description = "ISO-8601 UTC timestamp when the device was bound to the customer account", example = "2026-11-09T06:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant boundAt,

        @Schema(description = "ISO-8601 UTC timestamp when the 180-day hardware warranty expires", example = "2027-05-08T06:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant warrantyExpiresAt,

        @Schema(description = "Computed hardware warranty coverage status relative to the current server time", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        WarrantyStatus warrantyStatus,

        @Schema(description = "Linked parent LED hardware model details and technical specifications", requiredMode = Schema.RequiredMode.REQUIRED)
        ModelInfo model,

        @Schema(description = "Original Store order fulfillment reference, or null when no fulfillment linkage exists", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        FulfillmentInfo fulfillment
) {

    @Schema(description = "Linked parent LED hardware model details and technical specifications")
    @Builder
    public record ModelInfo(
            @Schema(description = "Unique LED hardware model identifier", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
            Long modelId,

            @Schema(description = "Linked Store product identifier, or null when not linked to a Store product", example = "101", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Long productId,

            @Schema(description = "Parent LED hardware model display name", example = "Lumintra Matrix 64x64 Pro", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String modelName,

            @Schema(description = "LED hardware model lifecycle status", example = "ACTIVE", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            LedModelStatus status,

            @Schema(description = "Structured technical specifications including canvas dimensions and custom attributes", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            LedModelListResponse.LedSpec spec
    ) {}

    @Schema(description = "Original Store order fulfillment reference for the LED device")
    @Builder
    public record FulfillmentInfo(
            @Schema(description = "Original purchase order identifier", example = "55", requiredMode = Schema.RequiredMode.REQUIRED)
            Long orderId,

            @Schema(description = "Original purchase order line item identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
            Long orderItemId
    ) {}
}
