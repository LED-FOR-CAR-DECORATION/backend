package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.shared.response.PaginationMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Schema(description = "Paginated response for designer commission queue")
@Builder
public record DesignerCommissionQueueResponse(
        @Schema(description = "List of commission tickets assigned to the authenticated designer")
        List<DesignerCommissionQueueItem> items,

        @Schema(description = "Pagination metadata")
        PaginationMeta meta
) {
    @Schema(description = "Individual commission item in designer queue")
    @Builder
    public record DesignerCommissionQueueItem(
            @Schema(description = "Unique commission request ID", example = "101")
            Long commissionId,

            @Schema(description = "Customer user account ID", example = "501")
            Long customerId,

            @Schema(description = "Customer full name", example = "Nguyen Van A", nullable = true)
            String customerName,

            @Schema(description = "Customer email address", example = "nguyenvana@gmail.com", nullable = true)
            String customerEmail,

            @Schema(description = "Commission request title", example = "Cyberpunk Neon Cat Animation")
            String title,

            @Schema(description = "Selected pricing tier code", example = "ADVANCED")
            String tierCode,

            @Schema(description = "Target LED matrix resolution", example = "32x64", nullable = true)
            String targetMatrixDimensions,

            @Schema(description = "Current commission lifecycle status across 13 states", example = "IN_PROGRESS")
            CommissionStatus status,

            @Schema(description = "Number of free revisions consumed", example = "0")
            int revisionsUsed,

            @Schema(description = "Maximum free revisions included in the commission", example = "2")
            int maxRevisions,

            @Schema(description = "Assigned staff employee ID", example = "3", nullable = true)
            Long assignedStaffId,

            @Schema(description = "Assigned staff employee full name", example = "Tran Thi Staff", nullable = true)
            String assignedStaffName,

            @Schema(description = "Assigned graphic designer employee ID", example = "5", nullable = true)
            Long assignedDesignerId,

            @Schema(description = "Assigned graphic designer employee full name", example = "Le Van Designer", nullable = true)
            String assignedDesignerName,

            @Schema(description = "Whether the commission processing has exceeded SLA timeline", example = "false")
            boolean isSlaOverdue,

            @Schema(description = "SLA due timestamp in UTC", example = "2026-11-20T12:00:00Z", nullable = true)
            Instant slaDueAt,

            @Schema(description = "Timestamp when the commission request was submitted in UTC", example = "2026-11-17T08:00:00Z")
            Instant createdAt,

            @Schema(description = "Timestamp when the commission request was last updated in UTC", example = "2026-11-17T12:00:00Z")
            Instant updatedAt
    ) {}
}
