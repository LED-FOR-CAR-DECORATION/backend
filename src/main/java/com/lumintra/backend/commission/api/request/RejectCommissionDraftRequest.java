package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for staff to reject a designer's commission draft internally")
public record RejectCommissionDraftRequest(
        @Schema(
                description = "Optional specific submission ID to reject. When omitted, the latest PENDING_STAFF_REVIEW draft for the commission is rejected.",
                example = "42"
        )
        @Positive(message = "submissionId must be greater than 0")
        Long submissionId,

        @Schema(
                description = "Internal QA review feedback note explaining what the designer needs to revise",
                example = "Neon glow edges are too dim on dark matrix areas; please increase contrast and smooth the loop transition.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "reviewNote must not be blank")
        @Size(max = 2000, message = "reviewNote must not exceed 2000 characters")
        String reviewNote
) {}
