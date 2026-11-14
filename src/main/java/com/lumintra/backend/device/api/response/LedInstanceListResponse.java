package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.shared.response.PaginationMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Schema(description = "Paginated list of physical LED hardware instances for internal inventory and tracking")
@Builder
public record LedInstanceListResponse(
        @Schema(description = "Paginated LED instance summaries sorted by instanceId descending", requiredMode = Schema.RequiredMode.REQUIRED)
        List<LedInstanceItem> items,

        @Schema(description = "Standard pagination metadata", requiredMode = Schema.RequiredMode.REQUIRED)
        PaginationMeta meta
) {
    public LedInstanceListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Schema(description = "Physical LED hardware instance tracking item")
    @Builder
    public record LedInstanceItem(
            @Schema(description = "Unique LED hardware instance identifier", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            Long instanceId,

            @Schema(description = "Parent LED hardware model identifier", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
            Long modelId,

            @Schema(description = "Parent LED hardware model display name", example = "Lumintra Matrix 64x64 Pro", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String modelName,

            @Schema(description = "Factory-assigned unique serial number", example = "LMT-64X64-SEED-0001", requiredMode = Schema.RequiredMode.REQUIRED)
            String serialNumber,

            @Schema(description = "Uppercase colon-separated ESP32 MAC address", example = "AA:BB:CC:11:22:01", requiredMode = Schema.RequiredMode.REQUIRED)
            String macAddress,

            @Schema(description = "PCB hardware revision stamped at provisioning", example = "REV_1_0", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String hardwareRevision,

            @Schema(description = "Customer-assigned display name once claimed", example = "Windshield Pro", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String deviceName,

            @Schema(description = "Physical unit lifecycle status", example = "IN_STOCK", requiredMode = Schema.RequiredMode.REQUIRED)
            LedInstanceStatus status,

            @Schema(description = "Linked Store order fulfillment identifiers, or null when not assigned to an order", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            FulfillmentInfo fulfillment,

            @Schema(description = "Customer owner contact summary once permanently bound, or null when unbound", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            OwnerInfo owner,

            @Schema(description = "ISO-8601 UTC timestamp when permanently bound and warranty started", example = "2026-11-09T06:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant boundAt,

            @Schema(description = "ISO-8601 UTC timestamp when 180-day hardware warranty expires", example = "2027-05-08T06:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant warrantyExpiresAt,

            @Schema(description = "ISO-8601 UTC timestamp when the hardware unit was manufactured", example = "2026-10-15T06:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant manufacturedAt,

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
