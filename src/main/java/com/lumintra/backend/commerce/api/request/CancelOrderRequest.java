package com.lumintra.backend.commerce.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Optional request payload containing cancellation reason")
public record CancelOrderRequest(
        @Size(max = 500, message = "reason must not exceed 500 characters")
        @Schema(description = "Optional reason for order cancellation", example = "Customer changed mind before dispatch", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String reason
) {
    public CancelOrderRequest {
        reason = reason == null ? null : reason.strip();
    }
}
