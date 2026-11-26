package com.lumintra.backend.commerce.api.response;

import com.lumintra.backend.commerce.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response payload returned after a customer cancels an order")
public record CancelOrderResponse(
        @Schema(description = "Unique order identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
        Long orderId,

        @Schema(description = "Order status prior to cancellation", example = "PENDING_PAYMENT", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus previousStatus,

        @Schema(description = "Updated order status persisted after cancellation (CANCELED or REFUNDED)", example = "CANCELED", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus status,

        @Schema(description = "ISO-8601 UTC timestamp when cancellation was recorded", example = "2026-11-13T13:20:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {}
