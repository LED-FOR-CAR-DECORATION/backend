package com.lumintra.backend.iam.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Saved customer delivery address; internal customerId is excluded")
public record UserAddressResponse(
        @Schema(
                description = "Unique identifier of the delivery address",
                example = "10",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long addressId,
        @Schema(
                description = "Recipient full name",
                example = "Nguyen Van A",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String recipientName,
        @Schema(
                description = "Recipient phone number",
                example = "0901234567",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String recipientPhone,
        @Schema(
                description = "Formatted full delivery address for Checkout and AhaMove",
                example = "123 Nguyen Hue, Ben Nghe, District 1, HCMC",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String fullAddress,
        @Schema(
                description = "Latitude coordinate (WGS84)",
                example = "10.7768890",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal latitude,
        @Schema(
                description = "Longitude coordinate (WGS84)",
                example = "106.7008060",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal longitude,
        @JsonProperty("isDefault")
        @Schema(
                description = "Whether this address is the customer's default delivery address",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isDefault,
        @Schema(
                description = "ISO-8601 UTC timestamp when the address was created",
                example = "2026-11-10T02:15:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(
                description = "ISO-8601 UTC timestamp when the address was last updated",
                example = "2026-11-10T02:15:00Z",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant updatedAt) {}
