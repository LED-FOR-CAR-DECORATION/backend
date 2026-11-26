package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Response returned after a designer successfully submits an artwork draft version")
@Builder
public record UploadCommissionDraftResponse(
        @Schema(description = "Unique submission identifier", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long submissionId,

        @Schema(description = "Unique commission request identifier", example = "105", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Assigned designer employee ID", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        Long designerId,

        @Schema(description = "Incremental draft version number", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        int versionNumber,

        @Schema(description = "Validated MED-01 artwork draft URL", example = "https://cdn.lumintra.shop/commissions/105/drafts/a1b2c3d4-neon-cat-v1.gif", requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,

        @Schema(description = "Optional designer note accompanying the draft", example = "First animated draft following the cyberpunk neon palette.")
        String designerNote,

        @Schema(description = "Optional linked COMMISSION character version ID", example = "25", nullable = true)
        Long characterVersionId,

        @Schema(description = "Lifecycle status of the draft submission", example = "PENDING_STAFF_REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionSubmissionStatus submissionStatus,

        @Schema(description = "Updated lifecycle status of the commission request, transitioned to INTERNAL_REVIEW", example = "INTERNAL_REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus commissionStatus,

        @Schema(description = "Timestamp when the draft submission was created in UTC", example = "2026-11-17T10:50:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt
) {}
