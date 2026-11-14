package com.lumintra.backend.payment.api.response;

import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "Outcome of processing a SePay payment webhook callback")
@Builder
public record SePayWebhookResponse(
        @Schema(description = "SePay transaction ID (sepay_tx_id) from the webhook payload", example = "92704", requiredMode = Schema.RequiredMode.REQUIRED)
        Long sepayTxId,

        @Schema(description = "Matched internal payment transaction ID", example = "5001", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long paymentId,

        @Schema(description = "Matched transfer payment code", example = "LMTORD102A1B2C3D4", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String paymentCode,

        @Schema(description = "Business target category", example = "ORDER", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        PaymentTargetType targetType,

        @Schema(description = "Identifier of the target order or commission resource", example = "102", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long targetId,

        @Schema(description = "Resulting payment status", example = "SUCCEEDED", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        PaymentStatus status,

        @Schema(description = "Webhook processing outcome (PROCESSED, IGNORED, or UNMATCHED)", example = "PROCESSED", requiredMode = Schema.RequiredMode.REQUIRED)
        String outcome,

        @Schema(description = "Human-readable processing summary", example = "Payment confirmed and PaymentSucceededEvent published.", requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {}
