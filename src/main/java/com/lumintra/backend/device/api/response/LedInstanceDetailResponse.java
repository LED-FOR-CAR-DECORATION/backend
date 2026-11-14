package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Detailed view of a physical LED hardware instance including model specs, warranty status, linked fulfillment info, owner summary, and re-printable factory QR payload")
@Builder
public record LedInstanceDetailResponse(
        @Schema(description = "Unique LED hardware instance identifier", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long instanceId,

        @Schema(description = "Parent LED hardware model details and technical specifications", requiredMode = Schema.RequiredMode.REQUIRED)
        ModelInfo model,

        @Schema(description = "Factory-assigned unique serial number", example = "LMT-64X64-SEED-0001", requiredMode = Schema.RequiredMode.REQUIRED)
        String serialNumber,

        @Schema(description = "Uppercase colon-separated ESP32 MAC address", example = "AA:BB:CC:11:22:01", requiredMode = Schema.RequiredMode.REQUIRED)
        String macAddress,

        @Schema(description = "PCB hardware revision stamped at provisioning", example = "REV_1_0", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String hardwareRevision,

        @Schema(description = "Identifier of the firmware version currently installed on this LED instance, if reported", example = "12", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long currentFirmwareId,

        @Schema(description = "Customer-assigned display name once claimed", example = "Windshield Pro", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String deviceName,

        @Schema(description = "Physical unit lifecycle status", example = "IN_STOCK", requiredMode = Schema.RequiredMode.REQUIRED)
        LedInstanceStatus status,

        @Schema(description = "Derived hardware warranty coverage status", example = "NOT_ACTIVATED", requiredMode = Schema.RequiredMode.REQUIRED)
        WarrantyStatus warrantyStatus,

        @Schema(description = "ISO-8601 UTC timestamp when permanently bound and warranty started", example = "2026-11-09T06:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant boundAt,

        @Schema(description = "ISO-8601 UTC timestamp when 180-day hardware warranty expires", example = "2027-05-08T06:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant warrantyExpiresAt,

        @Schema(description = "ISO-8601 UTC timestamp when the hardware unit was manufactured", example = "2026-10-15T06:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant manufacturedAt,

        @Schema(description = "Linked Store order fulfillment identifiers, or null when not assigned to an order", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        FulfillmentInfo fulfillment,

        @Schema(description = "Customer owner contact summary once permanently bound, or null when unbound", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        OwnerInfo owner,

        @Schema(
                description = "Compact HMAC-SHA256 signed factory QR JSON payload string for re-printing; suppressed (null) when status is BOUND or DECOMMISSIONED",
                example = "{\"v\":1,\"sn\":\"LMT-64X64-SEED-0001\",\"mac\":\"AA:BB:CC:11:22:01\",\"sig\":\"...\"}",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String qrPayload,

        @Schema(description = "ISO-8601 UTC timestamp when the record was created", example = "2026-11-14T06:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(description = "ISO-8601 UTC timestamp when the record was last updated", example = "2026-11-14T06:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant updatedAt
) {

    @Schema(description = "Parent LED hardware model details and technical specifications")
    @Builder
    public record ModelInfo(
            @Schema(description = "Unique LED hardware model identifier", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
            Long modelId,

            @Schema(description = "Linked Store product identifier, or null when the model is not yet linked to a Store product", example = "101", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Long productId,

            @Schema(description = "Parent LED hardware model display name", example = "Lumintra Matrix 64x64 Pro", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String modelName,

            @Schema(description = "LED model lifecycle status", example = "ACTIVE", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            LedModelStatus status,

            @Schema(description = "Structured technical specifications including canvas dimensions and ordered attributes", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            LedModelListResponse.LedSpec spec
    ) {}

    @Schema(description = "Linked order fulfillment reference for an LED instance")
    @Builder
    public record FulfillmentInfo(
            @Schema(description = "Linked order identifier", example = "55", requiredMode = Schema.RequiredMode.REQUIRED)
            Long orderId,

            @Schema(description = "Linked order line item identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
            Long orderItemId
    ) {}

    @Schema(description = "Customer owner contact summary for a bound LED instance")
    @Builder
    public record OwnerInfo(
            @Schema(description = "Owner customer account identifier", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
            Long customerId,

            @Schema(description = "Customer full name", example = "Nguyen Van A", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String fullName,

            @Schema(description = "Customer phone number", example = "0901234567", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String phone,

            @Schema(description = "Customer account email", example = "customer@example.com", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String email
    ) {}
}
