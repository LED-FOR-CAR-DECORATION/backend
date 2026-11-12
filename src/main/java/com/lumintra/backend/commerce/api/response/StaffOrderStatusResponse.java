package com.lumintra.backend.commerce.api.response;

import com.lumintra.backend.commerce.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response payload after manually transitioning an order status by internal staff")
public record StaffOrderStatusResponse(
        @Schema(description = "Unique order identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
        Long orderId,

        @Schema(description = "Order status prior to this transition", example = "DELIVERY_FAILED", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus previousStatus,

        @Schema(description = "Updated order status persisted after transition", example = "PROCESSING", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus status,

        @Schema(description = "ISO-8601 UTC timestamp when the transition was recorded", example = "2026-11-12T06:55:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {}
