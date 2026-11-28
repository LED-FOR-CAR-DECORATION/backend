package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Response containing details of a commission draft submission")
@Builder
public record CommissionSubmissionResponse(
        Long submissionId,
        Long commissionId,
        Long designerId,
        String designerName,
        int versionNumber,
        String imageUrl,
        String designerNote,
        CommissionSubmissionStatus status,
        Long reviewerStaffId,
        String reviewerStaffName,
        String staffReviewNote,
        Instant reviewedAt,
        Instant deliveredAt,
        Instant createdAt
) {
}

