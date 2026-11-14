package com.lumintra.backend.commerce.api.request;

import com.lumintra.backend.commerce.api.request.validation.StaffTargetOrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload for manually transitioning an order status by internal staff")
public record UpdateStaffOrderStatusRequest(
        @Schema(
                description = "Target order status allowed for manual staff transitions (CANCELLED normalizes to CANCELED)",
                example = "PROCESSING",
                allowableValues = {"PROCESSING", "CANCELED", "CANCELLED"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "status is required")
        @StaffTargetOrderStatus
        String status
) {
    public OrderStatus toOrderStatus() {
        String normalized = status.trim();
        if ("CANCELLED".equals(normalized)) {
            return OrderStatus.CANCELED;
        }
        return OrderStatus.valueOf(normalized);
    }
}
