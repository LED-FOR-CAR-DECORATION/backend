package com.lumintra.backend.device.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Schema(description = "Request body to stock in one or more physical LED hardware instances for a specific LED model")
public record StockInLedInstancesRequest(
        @Schema(description = "Target LED hardware model identifier", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "modelId is required")
        @Positive(message = "modelId must be greater than 0")
        Long modelId,

        @Schema(description = "List of physical LED instances (1 to 100 units per batch) to register as IN_STOCK", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "At least one LED instance item is required")
        @Size(max = 100, message = "Cannot stock in more than 100 LED instances in a single batch")
        List<@Valid StockInLedInstanceItem> items
) {
    public StockInLedInstancesRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Schema(description = "Individual physical LED unit metadata to register into warehouse stock")
    public record StockInLedInstanceItem(
            @Schema(description = "Unique hardware serial number", example = "LMT-64X64-2026-0011", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "serialNumber is required")
            @Size(max = 100, message = "serialNumber must not exceed 100 characters")
            String serialNumber,

            @Schema(description = "Unique ESP32 MAC address (XX:XX:XX:XX:XX:XX)", example = "AA:BB:CC:22:33:11", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "macAddress is required")
            @Pattern(
                    regexp = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$",
                    message = "macAddress must follow XX:XX:XX:XX:XX:XX hexadecimal format"
            )
            String macAddress,

            @Schema(description = "Optional manufacturing timestamp in ISO-8601 UTC (defaults to current time)", example = "2026-11-20T08:00:00Z", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant manufacturedAt
    ) {
        public StockInLedInstanceItem {
            if (serialNumber != null) {
                serialNumber = serialNumber.strip();
            }
            if (macAddress != null) {
                macAddress = macAddress.strip().toUpperCase(Locale.ROOT);
            }
        }
    }
}
