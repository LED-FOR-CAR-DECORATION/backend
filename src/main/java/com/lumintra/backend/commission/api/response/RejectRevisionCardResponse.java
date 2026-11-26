package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Outcome of rejecting a pending revision brief card")
public record RejectRevisionCardResponse(
        @Schema(description = "Commission ID", example = "105") Long commissionId,
        @Schema(description = "Rejected chat message ID", example = "402") Long messageId,
        @Schema(
                        description = "Current commission state (remains PREVIEW_DELIVERED)",
                        example = "PREVIEW_DELIVERED")
                CommissionStatus status) {}
