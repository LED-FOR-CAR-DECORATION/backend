package com.lumintra.backend.commerce.api.response;

import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.shared.response.PaginationMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Paginated list of physical fulfillment orders for internal staff operations")
public record StaffOrderListResponse(
        @Schema(description = "Paginated staff order summary records sorted by createdAt descending")
        List<StaffOrderSummaryItem> items,

        @Schema(description = "Standard pagination metadata")
        PaginationMeta meta
) {
    @Schema(description = "Summary card of a physical fulfillment order for staff queue management")
    @Builder
    public record StaffOrderSummaryItem(
            @Schema(description = "Unique order identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
            Long orderId,

            @Schema(description = "Order product category classification", example = "MIXED", requiredMode = Schema.RequiredMode.REQUIRED)
            OrderType orderType,

            @Schema(description = "Current order lifecycle status", example = "PROCESSING", requiredMode = Schema.RequiredMode.REQUIRED)
            OrderStatus status,

            @Schema(description = "Latest payment transaction status", example = "SUCCESS", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            PaymentStatus paymentStatus,

            @Schema(description = "Current delivery status from order_delivery", example = "PENDING", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            DeliveryStatus deliveryStatus,

            @Schema(description = "Customer account identifier", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
            Long customerId,

            @Schema(description = "Customer full name from IAM customer profile", example = "Nguyen Van A", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String customerFullName,

            @Schema(description = "Customer account email from IAM", example = "customer@example.com", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String customerEmail,

            @Schema(description = "Snapshotted delivery recipient name", example = "Dang Phuoc Nguyen", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String recipientName,

            @Schema(description = "Snapshotted delivery recipient phone number", example = "0912345678", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String recipientPhone,

            @Schema(description = "Final order payable amount in VND", example = "250000.00", requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal totalAmount,

            @Schema(description = "Total number of distinct order line items (COUNT of order_item)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            int totalItems,

            @Schema(description = "Display name of the primary (first) product in the order", example = "Lumintra LED Matrix v1", requiredMode = Schema.RequiredMode.REQUIRED)
            String primaryProductName,

            @Schema(description = "Thumbnail image URL of the primary (first) product in the order", example = "https://cdn.lumintra.com/products/led-thumb.png", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String primaryProductThumbnail,

            @Schema(description = "ISO-8601 UTC timestamp when the order was created", example = "2026-11-10T16:45:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant createdAt,

            @Schema(description = "ISO-8601 UTC timestamp when the order was last updated", example = "2026-11-10T16:50:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant updatedAt
    ) {}
}
