package com.lumintra.backend.commerce.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Locale;

@Schema(description = "Request payload for dispatching a packaged physical order to AhaMove")
public record DispatchOrderRequest(
        @Schema(
                description = "AhaMove service ID (e.g., SGN-BIKE) or group service ID (e.g., BIKE)",
                example = "SGN-BIKE",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "serviceId is required")
        @Size(max = 50, message = "serviceId must not exceed 50 characters")
        String serviceId,

        @Schema(
                description = "Optional delivery instructions for the driver",
                example = "Handle fragile LED package with care",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 500, message = "remarks must not exceed 500 characters")
        String remarks,

        @Schema(
                description = "Optional parcel weight in kilograms for AhaMove package_detail",
                example = "1.5",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @DecimalMin(value = "0.0", inclusive = false, message = "weight must be greater than 0")
        @DecimalMax(value = "10000.0", message = "weight must not exceed 10000 kg")
        BigDecimal weight
) {
    public DispatchOrderRequest {
        serviceId = serviceId != null ? serviceId.trim().toUpperCase(Locale.ROOT) : null;
        remarks = remarks != null && !remarks.isBlank() ? remarks.trim() : null;
    }
}
