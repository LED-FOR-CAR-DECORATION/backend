package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for designer submitting a draft artwork version to staff review")
public record SubmitCommissionDraftRequest(
        @Schema(description = "Public CDN/Storage URL of draft preview image", example = "https://cdn.lumintra.shop/commissions/draft-v1.gif")
        @NotBlank(message = "imageUrl must not be blank")
        @Size(max = 1024, message = "imageUrl must not exceed 1024 characters")
        String imageUrl,

        @Schema(description = "Optional note from designer explaining changes", example = "Initial draft matching customer references")
        String designerNote
) {
}

