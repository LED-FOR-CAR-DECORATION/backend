package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Response returned upon successful approval of a commission request, including quote expiration and active payment session")
@Builder
public record ApproveCommissionResponse(
        @Schema(description = "Unique commission request identifier", example = "105", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Customer artwork title", example = "Cyberpunk Neon Cat Animation", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "Updated lifecycle status of the commission request", example = "PENDING_PAYMENT", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus status,

        @Schema(description = "Employee account identifier of the staff member assigned to this ticket", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Long assignedStaffId,

        @Schema(description = "ISO-8601 UTC timestamp when customer payment quote expires (+72h)", example = "2026-11-20T11:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant quoteExpiresAt,

        @Schema(description = "Active SePay transfer reference code", example = "LMTCMS105", requiredMode = Schema.RequiredMode.REQUIRED)
        String activePaymentCode,

        @Schema(description = "Initial payable amount in VND", example = "420000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal payableAmount,

        @Schema(description = "SePay VietQR payment session details for initial commission deposit", requiredMode = Schema.RequiredMode.REQUIRED)
        PaymentSessionResponse paymentSession,

        @Schema(description = "ISO-8601 UTC timestamp when the commission request was updated", example = "2026-11-17T11:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {}
