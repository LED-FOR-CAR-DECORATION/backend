package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Response payload after successfully assigning a commission to a designer")
@Builder
public record AssignCommissionResponse(
        @Schema(description = "Unique commission request ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Current lifecycle status of the commission request", example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus status,

        @Schema(description = "Assigned staff employee ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Long assignedStaffId,

        @Schema(description = "Assigned designer employee ID", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        Long assignedDesignerId,

        @Schema(description = "SLA deadline for the first draft submission (+72h)", example = "2026-11-20T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant slaDueAt,

        @Schema(description = "Timestamp of the update", example = "2026-11-17T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {}
