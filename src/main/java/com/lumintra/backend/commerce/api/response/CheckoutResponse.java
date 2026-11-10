package com.lumintra.backend.commerce.api.response;

import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Order checkout summary with initialized payment intent details")
public record CheckoutResponse(
        @Schema(description = "Newly created order identifier", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
        Long orderId,

        @Schema(description = "Initialized payment transaction identifier", example = "5001", requiredMode = Schema.RequiredMode.REQUIRED)
        Long paymentId,

        @Schema(description = "Payment gateway used for the transaction", example = "SEPAY", requiredMode = Schema.RequiredMode.REQUIRED)
        PaymentGateway paymentGateway,

        @Schema(description = "Total order amount in VND", example = "759000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal totalAmount,

        @Schema(description = "Initial order status awaiting payment confirmation", example = "PENDING_PAYMENT", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus status,

        @Schema(description = "ISO-8601 UTC timestamp when unpaid checkout reservation expires", example = "2026-11-10T10:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt
) {}
