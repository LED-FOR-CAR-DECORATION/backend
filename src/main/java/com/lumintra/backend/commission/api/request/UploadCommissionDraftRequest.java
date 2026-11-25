package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for a designer to submit an artwork draft version for a commission")
public record UploadCommissionDraftRequest(
        @Schema(
                description = "objectKey returned by POST /api/media/upload-url with purpose COMMISSION_DRAFT for this commission",
                example = "commissions/105/drafts/123e4567-e89b-12d3-a456-426614174000-neon-cat-v1.gif",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "imageUrl must not be blank")
        @Size(max = 1024, message = "imageUrl must not exceed 1024 characters")
        String imageUrl,

        @Schema(
                description = "Optional draft notes from the designer for staff internal review",
                example = "First animated draft following the cyberpunk neon palette requested by customer."
        )
        @Size(max = 2000, message = "designerNote must not exceed 2000 characters")
        String designerNote,

        @Schema(
                description = "Required character version ID belonging to a COMMISSION character owned by the designer",
                example = "25",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "characterVersionId is required")
        @Positive(message = "characterVersionId must be positive")
        Long characterVersionId
) {}
