package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import com.lumintra.backend.shared.response.PaginationMeta;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
@Schema(description = "Paginated list of physical LED hardware devices owned by the authenticated customer, ordered newest-bound first")
public record CustomerDeviceListResponse(
        @Schema(description = "Owned LED hardware devices on the current page", requiredMode = Schema.RequiredMode.REQUIRED)
        @ArraySchema(schema = @Schema(implementation = CustomerDeviceItem.class, description = "Owned LED hardware devices"))
        List<CustomerDeviceItem> items,

        @Schema(description = "Standard pagination metadata", requiredMode = Schema.RequiredMode.REQUIRED)
        PaginationMeta meta
) {
    public CustomerDeviceListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Builder
    @Schema(description = "Summary of a customer-owned LED hardware device for mobile device list rendering and BLE discovery")
    public record CustomerDeviceItem(
            @Schema(description = "Unique LED hardware device identifier (instance ID)", example = "2001")
            Long deviceId,

            @Schema(description = "Unique factory serial number", example = "SN-2026-0001")
            String serialNumber,

            @Schema(description = "Hardware BLE MAC address in uppercase colon-separated format", example = "AA:BB:CC:DD:EE:01")
            String macAddress,

            @Schema(description = "User-friendly device display name", example = "Lumintra Neo 64x16 - Rear Window")
            String deviceName,

            @Schema(description = "Current lifecycle status of the hardware device", example = "BOUND")
            LedInstanceStatus status,

            @Schema(description = "Hardware model catalog ID", example = "101")
            Long modelId,

            @Schema(description = "Hardware model display name", example = "Lumintra Neo 64x16")
            String modelName,

            @Schema(description = "LED matrix pixel width from the hardware model specification", example = "64")
            Integer widthPx,

            @Schema(description = "LED matrix pixel height from the hardware model specification", example = "16")
            Integer heightPx,

            @Schema(description = "Currently reported firmware version ID on the device (nullable until reported)", example = "1", nullable = true)
            Long currentFirmwareId,

            @Schema(description = "Timestamp when the device was bound to the customer account", example = "2026-11-14T10:15:30Z")
            Instant boundAt,

            @Schema(description = "Timestamp when the hardware warranty expires", example = "2027-11-14T10:15:30Z")
            Instant warrantyExpiresAt,

            @Schema(description = "Computed warranty status relative to the current server time", example = "ACTIVE")
            WarrantyStatus warrantyStatus
    ) {
    }
}
