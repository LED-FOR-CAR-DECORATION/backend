package com.lumintra.backend.commerce.api.request;

import com.lumintra.backend.commerce.api.request.validation.StaffTargetOrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for manually transitioning an order status by internal staff")
public record UpdateStaffOrderStatusRequest(
        @Schema(
                description = "Target order status allowed for manual staff transitions (CANCELLED normalizes to CANCELED)",
                example = "PROCESSING",
                allowableValues = {"PROCESSING", "REFUNDED", "CANCELED", "CANCELLED"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "status is required")
        @StaffTargetOrderStatus
        String status,

        @Schema(
                description = "Optional operational reason or refund/failure note recorded by staff",
                example = "Khách từ chối nhận hàng sau 2 lần giao lại, xác nhận hoàn tiền",
                maxLength = 500,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason
) {
    public UpdateStaffOrderStatusRequest(String status) {
        this(status, null);
    }

    public OrderStatus toOrderStatus() {
        String normalized = status.trim();
        if ("CANCELLED".equals(normalized)) {
            return OrderStatus.CANCELED;
        }
        return OrderStatus.valueOf(normalized);
    }

    public String normalizedReason() {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
