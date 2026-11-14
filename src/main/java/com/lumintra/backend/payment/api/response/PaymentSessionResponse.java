package com.lumintra.backend.payment.api.response;

import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "SePay VietQR payment session details and live status for QR modal display and status polling")
@Builder
public record PaymentSessionResponse(
        @Schema(description = "Unique payment transaction identifier", example = "5001", requiredMode = Schema.RequiredMode.REQUIRED)
        Long paymentId,

        @Schema(description = "Unique transfer code embedded in the VietQR description memo", example = "LUMORD102A1B2C3D4", requiredMode = Schema.RequiredMode.REQUIRED)
        String paymentCode,

        @Schema(description = "Business target category for this payment session", example = "ORDER", requiredMode = Schema.RequiredMode.REQUIRED)
        PaymentTargetType targetType,

        @Schema(description = "Identifier of the target order or commission resource", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
        Long targetId,

        @Schema(description = "Payment gateway provider name", example = "SEPAY", requiredMode = Schema.RequiredMode.REQUIRED)
        String gateway,

        @Schema(description = "Dynamic SePay VietQR image URL (populated while PENDING)", example = "https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=LUMORD102A1B2C3D4&showinfo=true&holder=NGUYEN%20QUANG%20KHANH", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String qrUrl,

        @Schema(description = "Beneficiary bank short name", example = "MBBank", requiredMode = Schema.RequiredMode.REQUIRED)
        String bankName,

        @Schema(description = "Beneficiary bank account number", example = "0123456789", requiredMode = Schema.RequiredMode.REQUIRED)
        String accountNumber,

        @Schema(description = "Beneficiary bank account holder name", example = "LUMINTRA", requiredMode = Schema.RequiredMode.REQUIRED)
        String accountName,

        @Schema(description = "Payable amount in VND", example = "250000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount,

        @Schema(description = "Current payment session lifecycle status", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
        PaymentStatus status,

        @Schema(description = "ISO-8601 UTC timestamp when this payment session expires", example = "2026-11-12T17:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant expiresAt,

        @Schema(description = "ISO-8601 UTC timestamp when payment was confirmed (null until settled)", example = "2026-11-12T16:48:12Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant paidAt,

        @Schema(description = "ISO-8601 UTC timestamp when the payment session was created", example = "2026-11-12T16:45:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt
) {}
