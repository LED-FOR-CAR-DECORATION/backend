package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Response returned after the customer approves the Deliverable Card and the commission is completed")
@Builder
public record ApproveDeliverableCardResponse(
        @Schema(description = "Unique commission request identifier", example = "105", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Updated commission lifecycle status, transitioned to COMPLETED", example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus commissionStatus,

        @Schema(description = "Identifier of the submission accepted by the customer", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long approvedSubmissionId,

        @Schema(description = "Version number of the accepted submission", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        int versionNumber,

        @Schema(description = "Updated status of the accepted submission (Deliverable Card accepted)", example = "CUSTOMER_ACCEPTED", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionSubmissionStatus submissionStatus,

        @Schema(description = "UTC timestamp when the customer approved the Deliverable Card", example = "2026-11-18T10:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant completedAt,

        @Schema(description = "SYSTEM chat message recording the approval, broadcast to the commission chat room", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionMessageResponse systemMessage
) {}
