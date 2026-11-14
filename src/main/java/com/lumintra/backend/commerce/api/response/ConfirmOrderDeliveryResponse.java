package com.lumintra.backend.commerce.api.response;

import com.lumintra.backend.commerce.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response payload returned after a customer confirms receipt of a delivered order")
public record ConfirmOrderDeliveryResponse(
        @Schema(description = "Unique order identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
        Long orderId,

        @Schema(description = "Order status prior to confirmation", example = "DELIVERED", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus previousStatus,

        @Schema(description = "Updated order status persisted after confirmation", example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus status,

        @Schema(description = "ISO-8601 UTC timestamp when delivery confirmation was recorded", example = "2026-11-12T14:25:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {}
