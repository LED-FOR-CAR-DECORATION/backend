package com.lumintra.backend.commerce.api.response;

import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Detailed order view for internal staff fulfillment including customer profile, snapshotted delivery details, line items, and payment reconciliation summary")
@Builder
public record StaffOrderDetailResponse(
        @Schema(description = "Unique order identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
        Long orderId,

        @Schema(description = "Order product category classification", example = "MIXED", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderType orderType,

        @Schema(description = "Current order lifecycle status", example = "PROCESSING", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus status,

        @Schema(description = "Sum of snapshotted line item subtotals in VND", example = "250000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal subtotal,

        @Schema(description = "Shipping fee applied to this order in VND", example = "0.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal shippingFee,

        @Schema(description = "Final payable total in VND", example = "250000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal totalAmount,

        @Schema(description = "ISO-8601 UTC payment expiration timestamp (populated only when status is PENDING_PAYMENT)", example = "2026-11-10T17:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant expiresAt,

        @Schema(description = "ISO-8601 UTC timestamp when the order was created", example = "2026-11-10T16:45:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(description = "ISO-8601 UTC timestamp when the order was last updated", example = "2026-11-10T16:50:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant updatedAt,

        @Schema(description = "Customer account contact summary from IAM", requiredMode = Schema.RequiredMode.REQUIRED)
        CustomerSummary customer,

        @Schema(description = "Ordered line items with immutable unit price snapshots", requiredMode = Schema.RequiredMode.REQUIRED)
        List<OrderItemDetail> items,

        @Schema(description = "Snapshotted recipient shipping details and carrier tracking info (null if order has no delivery)", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        DeliveryDetail delivery,

        @Schema(description = "Latest payment reconciliation summary without customer VietQR retry details", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        StaffPaymentDetail payment
) {
    @Schema(description = "Customer contact summary from IAM")
    public record CustomerSummary(
            @Schema(description = "Customer account identifier", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
            Long customerId,

            @Schema(description = "Customer full name", example = "Nguyen Van A", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String fullName,

            @Schema(description = "Customer phone number", example = "0909111222", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String phone,

            @Schema(description = "Customer account email", example = "customer@example.com", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String email
    ) {}

    @Schema(description = "Snapshotted line item within an order")
    @Builder
    public record OrderItemDetail(
            @Schema(description = "Order item identifier", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            Long orderItemId,

            @Schema(description = "Product identifier", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
            Long productId,

            @Schema(description = "Product display name", example = "Lumintra LED Matrix v1", requiredMode = Schema.RequiredMode.REQUIRED)
            String productName,

            @Schema(description = "Product type classification", example = "LED", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            ProductType productType,

            @Schema(description = "Product thumbnail image URL", example = "https://cdn.lumintra.com/products/led-v1.png", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String thumbnailUrl,

            @Schema(description = "Preview GIF animation URL (populated for CHARACTER products)", example = "https://cdn.lumintra.com/characters/cat-preview.gif", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String previewGifUrl,

            @Schema(description = "Snapshotted unit price at checkout time in VND", example = "200000.00", requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal unitPrice,

            @Schema(description = "Purchased quantity", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            int quantity,

            @Schema(description = "Snapshotted line subtotal (unitPrice * quantity) in VND", example = "200000.00", requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal subtotal
    ) {}

    @Schema(description = "Snapshotted recipient shipping details and operational delivery metrics")
    @Builder
    public record DeliveryDetail(
            @Schema(description = "Assigned shipping carrier", example = "AHAMOVE", requiredMode = Schema.RequiredMode.REQUIRED)
            String carrier,

            @Schema(description = "Snapshotted recipient full name", example = "Dang Phuoc Nguyen", requiredMode = Schema.RequiredMode.REQUIRED)
            String recipientName,

            @Schema(description = "Snapshotted recipient phone number", example = "0912345678", requiredMode = Schema.RequiredMode.REQUIRED)
            String recipientPhone,

            @Schema(description = "Snapshotted full delivery street address", example = "Saigon Hi-Tech Park, Thu Duc City, HCMC", requiredMode = Schema.RequiredMode.REQUIRED)
            String fullAddress,

            @Schema(description = "Snapshotted delivery latitude coordinate", example = "10.8411000", requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal latitude,

            @Schema(description = "Snapshotted delivery longitude coordinate", example = "106.8099000", requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal longitude,

            @Schema(description = "Current shipment delivery status", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
            DeliveryStatus deliveryStatus,

            @Schema(description = "Carrier waybill tracking number", example = "AHV-2026-99881", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String trackingNumber,

            @Schema(description = "Carrier live map tracking URL", example = "https://ahamove.com/track/AHV-2026-99881", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String trackingUrl,

            @Schema(description = "Shipping fee charged by carrier in VND", example = "32000.00", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            BigDecimal carrierFee,

            @Schema(description = "Estimated delivery timestamp in UTC", example = "2026-11-11T10:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant estimatedDeliveryAt,

            @Schema(description = "Actual delivered timestamp in UTC", example = "2026-11-11T09:42:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant deliveredAt
    ) {}

    @Schema(description = "Payment reconciliation summary for internal operations")
    @Builder
    public record StaffPaymentDetail(
            @Schema(description = "Payment gateway used", example = "SEPAY", requiredMode = Schema.RequiredMode.REQUIRED)
            PaymentGateway paymentGateway,

            @Schema(description = "Latest payment transaction status", example = "SUCCESS", requiredMode = Schema.RequiredMode.REQUIRED)
            PaymentStatus status,

            @Schema(description = "Bank/gateway reference transaction code (settled payments only)", example = "SEPAY-FT263149981", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String bankReferenceCode,

            @Schema(description = "Confirmed paid amount in VND (settled payments only)", example = "250000.00", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            BigDecimal paidAmount,

            @Schema(description = "ISO-8601 UTC timestamp when payment was confirmed (settled payments only)", example = "2026-11-10T16:48:12Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant paidAt
    ) {}
}
