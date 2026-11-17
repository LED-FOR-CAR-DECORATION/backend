package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload for staff reviewing a designer's draft submission")
public record ReviewCommissionSubmissionRequest(
        @Schema(description = "Review decision action (APPROVE or REQUEST_REVISION)", example = "APPROVE")
        @NotNull(message = "action must not be null")
        ReviewAction action,

        @Schema(description = "Staff review feedback note required when requesting revisions", example = "Colors need to be brighter")
        String staffReviewNote
) {
    public enum ReviewAction {
        APPROVE,
        REQUEST_REVISION
    }
}

