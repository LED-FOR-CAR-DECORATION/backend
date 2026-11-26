package com.lumintra.backend.dashboard.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Detailed order audit breakdown for manager dashboard drill-down")
public record ManagerOrderAuditDetailResponse(
        Long orderId,
        String referenceCode,
        String customerName,
        String customerEmail,
        String status,
        String orderType,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        Instant createdAt,
        String paymentCode,
        String paymentGateway,
        String paymentStatus,
        Instant paidAt,
        List<OrderLineItem> items
) {
    public record OrderLineItem(
            Long orderItemId,
            Long productId,
            String productName,
            String category,
            long quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}
}
