package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Response returned after successfully rejecting an initial commission request")
@Builder
public record RejectCommissionResponse(
        @Schema(description = "Unique commission request identifier", example = "105", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Commission lifecycle status, transitioned to CANCELLED", example = "CANCELLED", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus status,

        @Schema(description = "ID of the staff who rejected/handled the commission", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Long assignedStaffId,

        @Schema(description = "Recorded rejection reason", example = "Artwork request exceeds current hardware matrix limits.", requiredMode = Schema.RequiredMode.REQUIRED)
        String rejectionReason,

        @Schema(description = "Timestamp when the commission was updated in UTC", example = "2026-11-17T14:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {}
