package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Confirmed revision and optional extra-fee payment session")
public record ConfirmRevisionCardResponse(
        @Schema(description = "Commission ID", example = "105") Long commissionId,
        @Schema(description = "Confirmed chat message ID", example = "402") Long messageId,
        @Schema(description = "Current commission state", example = "CUSTOMER_REVISION_REQUESTED")
                CommissionStatus status,
        @Schema(
                        description =
                                "Consumed customer revisions; paid revision is consumed after"
                                        + " payment",
                        example = "1")
                int revisionsUsed,
        @Schema(description = "Accumulated extra revision fee in VND", example = "0.00")
                BigDecimal extraRevisionFee,
        @Schema(
                        description = "Payment session for paid revisions; null for free revisions",
                        nullable = true)
                PaymentSessionResponse paymentSession) {}
