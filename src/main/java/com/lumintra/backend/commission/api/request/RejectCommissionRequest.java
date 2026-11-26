package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for rejecting an initial commission request")
public record RejectCommissionRequest(
        @Schema(
                description = "Detailed reason why the commission request was rejected",
                example = "Artwork request exceeds current hardware matrix limits."
        )
        @NotBlank(message = "rejectionReason must not be blank")
        @Size(max = 1000, message = "rejectionReason must not exceed 1000 characters")
        String rejectionReason
) {}
