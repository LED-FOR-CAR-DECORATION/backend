package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Response returned after staff rejects a designer's draft internally without consuming customer revision quota")
@Builder
public record RejectCommissionDraftResponse(
        @Schema(description = "Unique commission request identifier", example = "105", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Rejected submission identifier", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long submissionId,

        @Schema(description = "Draft version number that was rejected", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        int versionNumber,

        @Schema(description = "Updated submission status (STAFF_REJECTED)", example = "STAFF_REJECTED", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionSubmissionStatus submissionStatus,

        @Schema(description = "Updated commission lifecycle status (INTERNAL_REVISION_REQUIRED)", example = "INTERNAL_REVISION_REQUIRED", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus commissionStatus,

        @Schema(description = "Employee ID of the staff who performed the internal quality review", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Long reviewerStaffId,

        @Schema(description = "Recorded internal review note for the designer", example = "Neon glow edges are too dim on dark matrix areas.", requiredMode = Schema.RequiredMode.REQUIRED)
        String staffReviewNote,

        @Schema(description = "Customer revision quota used (unchanged — 0 consumed by internal review)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int revisionsUsed,

        @Schema(description = "Maximum included customer revisions", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        int maxRevisions,

        @Schema(description = "Timestamp when the internal review was recorded in UTC", example = "2026-11-17T12:15:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant reviewedAt
) {}
