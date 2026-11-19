package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.RevisionEstimateSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Result of applying a revision delivery estimate")
@Builder
public record InputRevisionEstimateResponse(
        @Schema(description = "Commission request ID", example = "105", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Commission status after the estimate", example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus status,

        @Schema(description = "Revision request being worked on", example = "31", requiredMode = Schema.RequiredMode.REQUIRED)
        Long revisionRequestId,

        @Schema(description = "1-based revision sequence number", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        int revisionNumber,

        @Schema(description = "Estimated hours to deliver the revised draft", example = "36", requiredMode = Schema.RequiredMode.REQUIRED)
        int estimatedHours,

        @Schema(description = "Who set the estimate", example = "DESIGNER", requiredMode = Schema.RequiredMode.REQUIRED)
        RevisionEstimateSource estimateSource,

        @Schema(description = "Designer's internal delivery deadline (commission sla_due_at), UTC", example = "2026-11-21T10:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant slaDueAt,

        @Schema(description = "Delivery deadline shown to the customer (slaDueAt + 24h buffer), UTC", example = "2026-11-22T10:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant customerDeliveryDeadline,

        @Schema(description = "When the estimate was applied, UTC", example = "2026-11-19T22:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant estimatedAt
) {}
