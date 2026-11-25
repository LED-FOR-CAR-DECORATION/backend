package com.lumintra.backend.commerce.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request payload for staff to update recipient delivery contact/address before dispatching or redelivering an order")
public record UpdateStaffOrderDeliveryAddressRequest(
        @Schema(description = "Updated recipient full name", example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "recipientName is required")
        @Size(max = 100, message = "recipientName must not exceed 100 characters")
        String recipientName,

        @Schema(description = "Updated recipient phone number", example = "0909123456", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "recipientPhone is required")
        @Size(max = 20, message = "recipientPhone must not exceed 20 characters")
        String recipientPhone,

        @Schema(description = "Updated full street delivery address", example = "123 Le Loi, Ben Nghe, District 1, Ho Chi Minh City", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "fullAddress is required")
        @Size(max = 500, message = "fullAddress must not exceed 500 characters")
        String fullAddress,

        @Schema(description = "Optional updated latitude coordinate (retains existing coordinate if omitted)", example = "10.7768890", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @DecimalMin(value = "-90.0", message = "latitude must be >= -90.0")
        @DecimalMax(value = "90.0", message = "latitude must be <= 90.0")
        BigDecimal latitude,

        @Schema(description = "Optional updated longitude coordinate (retains existing coordinate if omitted)", example = "106.7008060", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @DecimalMin(value = "-180.0", message = "longitude must be >= -180.0")
        @DecimalMax(value = "180.0", message = "longitude must be <= 180.0")
        BigDecimal longitude
) {}
