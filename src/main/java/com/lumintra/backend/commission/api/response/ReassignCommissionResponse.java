package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "CMS-25: commission assignment after a manager/admin reassignment")
@Builder
public record ReassignCommissionResponse(
        @Schema(description = "Unique commission request ID", example = "105", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Lifecycle status (never changed by a reassignment)", example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus status,

        @Schema(description = "Assigned staff employee ID", example = "4", nullable = true)
        Long assignedStaffId,

        @Schema(description = "Assigned designer employee ID", example = "6", nullable = true)
        Long assignedDesignerId,

        @Schema(description = "When the designer owes the next deliverable; reset when a designer is reassigned during work", example = "2026-11-23T12:00:00Z", nullable = true)
        Instant slaDueAt,

        @Schema(description = "Whether sla_due_at has passed", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isSlaOverdue,

        @Schema(description = "Timestamp of the update", example = "2026-11-20T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {}
