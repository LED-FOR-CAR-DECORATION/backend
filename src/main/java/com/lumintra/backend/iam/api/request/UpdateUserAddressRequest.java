package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(
        description =
                "Request payload to update an existing customer delivery address using Goong Map"
                        + " formatted address and WGS84 coordinates (preserves existing isDefault)")
public record UpdateUserAddressRequest(
        @Schema(
                        description = "Recipient full name, trimmed, 1 to 100 characters",
                        example = "Nguyen Van B",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "recipientName is required")
                @Size(max = 100, message = "recipientName must not exceed 100 characters")
                String recipientName,
        @Schema(
                        description =
                                "Recipient phone number (8 to 15 digits with an optional leading"
                                        + " plus)",
                        example = "0912345678",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "recipientPhone is required")
                @Size(max = 20, message = "recipientPhone must not exceed 20 characters")
                @Pattern(
                        regexp = "^\\+?[0-9]{8,15}$",
                        message =
                                "recipientPhone must contain 8 to 15 digits with an optional"
                                        + " leading plus")
                String recipientPhone,
        @Schema(
                        description =
                                "Full formatted delivery address from Goong Map, trimmed, 1 to"
                                        + " 255 characters",
                        example = "456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "fullAddress is required")
                @Size(max = 255, message = "fullAddress must not exceed 255 characters")
                String fullAddress,
        @Schema(
                        description =
                                "WGS84 latitude coordinate (-90 to 90); rounded to 7 decimal"
                                        + " places on save",
                        example = "10.7731235",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull(message = "latitude is required")
                @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
                @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
                BigDecimal latitude,
        @Schema(
                        description =
                                "WGS84 longitude coordinate (-180 to 180); rounded to 7 decimal"
                                        + " places on save",
                        example = "106.6987654",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull(message = "longitude is required")
                @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
                @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
                BigDecimal longitude) {
    public UpdateUserAddressRequest {
        recipientName = recipientName == null ? null : recipientName.strip();
        recipientPhone = recipientPhone == null ? null : recipientPhone.strip();
        fullAddress = fullAddress == null ? null : fullAddress.strip();
    }
}
