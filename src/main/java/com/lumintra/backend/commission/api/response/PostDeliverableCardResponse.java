package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Response returned after staff approves a designer draft and posts a Deliverable Card to the customer chat")
@Builder
public record PostDeliverableCardResponse(
        @Schema(description = "Unique commission request identifier", example = "105", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Updated commission lifecycle status, transitioned to PREVIEW_DELIVERED", example = "PREVIEW_DELIVERED", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus commissionStatus,

        @Schema(description = "Identifier of the approved draft submission", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long approvedSubmissionId,

        @Schema(description = "Version number of the approved draft", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        int versionNumber,

        @Schema(description = "Updated status of the approved draft submission", example = "STAFF_APPROVED", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionSubmissionStatus submissionStatus,

        @Schema(description = "UTC timestamp when the commission auto-completes if the customer takes no action (+7 days)", example = "2026-11-24T10:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant autoCompleteAt,

        @Schema(description = "Persisted DELIVERABLE_CARD chat message broadcast to the commission chat room", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionMessageResponse deliverableCard
) {}
