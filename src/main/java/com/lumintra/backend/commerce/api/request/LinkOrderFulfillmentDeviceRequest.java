package com.lumintra.backend.commerce.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Payload to link a physical LED unit to an order item via serial number")
public record LinkOrderFulfillmentDeviceRequest(
        @Schema(
                description = "ID of the order item to be fulfilled (retrieved from GET /api/management/staff/orders/{id})",
                example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "orderItemId must not be null")
        @Positive(message = "orderItemId must be positive")
        Long orderItemId,

        @Schema(
                description = "Serial number printed on the LED unit label or packaging",
                example = "LMT-64X64-SEED-0001",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "serialNumber must not be blank")
        String serialNumber
) {
    public LinkOrderFulfillmentDeviceRequest {
        serialNumber = serialNumber != null ? serialNumber.strip() : null;
    }
}
