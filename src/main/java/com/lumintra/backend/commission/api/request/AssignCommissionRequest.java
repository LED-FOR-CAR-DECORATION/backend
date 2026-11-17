package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request payload to assign a paid commission ticket to an internal designer")
public record AssignCommissionRequest(
        @NotNull(message = "Designer ID is required")
        @Positive(message = "Designer ID must be greater than 0")
        @Schema(description = "Target designer employee ID", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        Long designerId
) {}
