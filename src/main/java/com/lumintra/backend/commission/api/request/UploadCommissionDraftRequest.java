package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for a designer to submit an artwork draft version for a commission")
public record UploadCommissionDraftRequest(
        @Schema(
                description = "Public media URL of the artwork draft uploaded via MED-01 with purpose COMMISSION_DRAFT",
                example = "https://cdn.lumintra.shop/commissions/105/drafts/a1b2c3d4-neon-cat-v1.gif",
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
        String designerNote
) {}
