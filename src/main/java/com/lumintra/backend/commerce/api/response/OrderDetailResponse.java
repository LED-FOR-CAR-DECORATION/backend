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

@Schema(description = "Detailed purchase order view including line items, optional delivery tracking, and payment status/retry info")
@Builder
public record OrderDetailResponse(
        @Schema(description = "Unique order identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
        Long orderId,

        @Schema(description = "Order product category classification", example = "MIXED", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderType orderType,

        @Schema(description = "Current order lifecycle status", example = "PENDING_PAYMENT", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus status,

        @Schema(description = "Sum of snapshotted line item subtotals in VND", example = "250000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal subtotal,

        @Schema(description = "Shipping fee applied to this order in VND", example = "0.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal shippingFee,

        @Schema(description = "Final payable total in VND", example = "250000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal totalAmount,

        @Schema(description = "ISO-8601 UTC payment expiration timestamp (populated when status is PENDING_PAYMENT)", example = "2026-11-10T17:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant expiresAt,

        @Schema(description = "ISO-8601 UTC timestamp when the order was created", example = "2026-11-10T16:45:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(description = "Ordered line items with immutable unit price snapshots", requiredMode = Schema.RequiredMode.REQUIRED)
        List<OrderItemDetail> items,

        @Schema(description = "Snapshotted recipient address and AhaMove tracking details (null for digital-only orders)", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        DeliveryDetail delivery,

        @Schema(description = "Latest payment transaction confirmation or active SePay VietQR retry instructions", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        PaymentDetail payment
) {
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

    @Schema(description = "Snapshotted recipient shipping details and live carrier tracking status")
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

            @Schema(description = "Estimated delivery timestamp in UTC", example = "2026-11-11T10:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant estimatedDeliveryAt
    ) {}

    @Schema(description = "Payment details showing active SePay VietQR retry info when PENDING_PAYMENT or settled payment confirmation when SUCCESS")
    @Builder
    public record PaymentDetail(
            @Schema(description = "Payment gateway used", example = "SEPAY", requiredMode = Schema.RequiredMode.REQUIRED)
            PaymentGateway paymentGateway,

            @Schema(description = "Current payment transaction status", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
            PaymentStatus status,

            @Schema(description = "Bank transfer memo content required for SePay matching (active PENDING_PAYMENT only)", example = "LMTORD102A1B2C3D4", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String paymentContent,

            @Schema(description = "Dynamic SePay VietQR image URL (null when paid or expired)", example = "https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=LMTORD102A1B2C3D4&showinfo=true&holder=NGUYEN%20QUANG%20KHANH", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String qrCodeUrl,

            @Schema(description = "Receiving bank account number (active PENDING_PAYMENT only)", example = "0123456789", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String bankAccount,

            @Schema(description = "Receiving bank code/name (active PENDING_PAYMENT only)", example = "MBBank", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String bankName,

            @Schema(description = "ISO-8601 UTC expiration timestamp for pending payment", example = "2026-11-10T17:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant expiresAt,

            @Schema(description = "Bank/gateway reference transaction code (settled payments)", example = "SEPAY-FT263149981", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String bankReferenceCode,

            @Schema(description = "Confirmed paid amount in VND (settled payments)", example = "250000.00", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            BigDecimal paidAmount,

            @Schema(description = "ISO-8601 UTC timestamp when payment was confirmed (settled payments)", example = "2026-11-10T16:48:12Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant paidAt
    ) {}
}
