package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.shared.response.PaginationMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Paginated list of personal commission request summaries for the authenticated customer")
@Builder
public record CustomerCommissionListResponse(
        @Schema(description = "Paginated list of customer commission summary items")
        List<CustomerCommissionSummaryItem> items,

        @Schema(description = "Standard pagination metadata")
        PaginationMeta meta
) {
    @Schema(description = "Summary card of a personal custom artwork commission request")
    @Builder
    public record CustomerCommissionSummaryItem(
            @Schema(description = "Unique commission request ID", example = "501")
            Long commissionId,

            @Schema(description = "Commission request title", example = "Cyberpunk Neko Pixel Animation")
            String title,

            @Schema(description = "Selected pricing tier code", example = "ADVANCED")
            String tierCode,

            @Schema(description = "Target LED matrix resolution", example = "32x64", nullable = true)
            String targetMatrixDimensions,

            @Schema(description = "Current commission lifecycle status", example = "PENDING_PAYMENT")
            CommissionStatus status,

            @Schema(description = "Initial package/configuration price in VND", example = "180000.00")
            BigDecimal initialPrice,

            @Schema(description = "Accumulated extra revision fee in VND", example = "0.00")
            BigDecimal extraRevisionFee,

            @Schema(description = "Total commission price in VND (initialPrice + extraRevisionFee)", example = "180000.00")
            BigDecimal totalPrice,

            @Schema(description = "Number of free revisions consumed", example = "0")
            int revisionsUsed,

            @Schema(description = "Maximum free revisions included in the commission", example = "3")
            int maxRevisions,

            @Schema(description = "Active SePay payment code when status is PENDING_PAYMENT or AWAITING_EXTRA_PAYMENT", example = "LMT-CMS-501", nullable = true)
            String activePaymentCode,

            @Schema(description = "Quotation payment expiration timestamp in UTC", example = "2026-11-19T10:00:00Z", nullable = true)
            Instant quoteExpiresAt,

            @Schema(description = "Timestamp when the commission request was submitted in UTC", example = "2026-11-16T08:00:00Z")
            Instant createdAt,

            @Schema(description = "Timestamp when the commission request was last updated in UTC", example = "2026-11-16T09:00:00Z")
            Instant updatedAt
    ) {}
}
