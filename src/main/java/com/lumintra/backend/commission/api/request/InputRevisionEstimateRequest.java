package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Designer's delivery estimate for the confirmed revision (BR-FL3-14)")
public record InputRevisionEstimateRequest(
        @Schema(
                description = "Hours the designer needs to deliver the revised draft (1..72). The customer sees this plus a 24h buffer.",
                example = "36",
                minimum = "1",
                maximum = "72",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "estimatedHours is required")
        @Min(value = 1, message = "estimatedHours must be at least 1")
        @Max(value = 72, message = "estimatedHours must not exceed 72")
        Integer estimatedHours
) {}
