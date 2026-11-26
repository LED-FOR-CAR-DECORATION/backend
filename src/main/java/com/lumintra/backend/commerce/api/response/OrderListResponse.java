package com.lumintra.backend.commerce.api.response;

import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.shared.response.PaginationMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Paginated list of order summary cards for the authenticated customer")
public record OrderListResponse(
        @Schema(description = "Paginated order summary records sorted by createdAt descending")
        List<OrderSummaryItem> items,

        @Schema(description = "Standard pagination metadata")
        PaginationMeta meta
) {
    @Schema(description = "Summary card of a customer purchase order")
    @Builder
    public record OrderSummaryItem(
            @Schema(description = "Unique order identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
            Long orderId,

            @Schema(description = "Order product category classification", example = "MIXED", requiredMode = Schema.RequiredMode.REQUIRED)
            OrderType orderType,

            @Schema(description = "Current order lifecycle status", example = "PENDING_PAYMENT", requiredMode = Schema.RequiredMode.REQUIRED)
            OrderStatus status,

            @Schema(description = "Final order payable amount in VND", example = "250000.00", requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal totalAmount,

            @Schema(description = "Total number of distinct order line items (COUNT of order_item)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            int totalItems,

            @Schema(description = "Display name of the primary (first) product in the order", example = "Lumintra LED Matrix v1", requiredMode = Schema.RequiredMode.REQUIRED)
            String primaryProductName,

            @Schema(description = "Thumbnail image URL of the primary (first) product in the order", example = "https://cdn.lumintra.com/products/led-thumb.png", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String primaryProductThumbnail,

            @Schema(description = "ISO-8601 UTC payment expiration timestamp (populated only when status is PENDING_PAYMENT)", example = "2026-11-10T17:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant expiresAt,

            @Schema(description = "ISO-8601 UTC timestamp when the order was created", example = "2026-11-10T16:45:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant createdAt
    ) {}
}
