package com.lumintra.backend.commerce.api.request;

import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Query parameters for listing and filtering orders in the staff fulfillment queue")
public record StaffOrderFilterRequest(
        @Schema(
                description = "Filter by order lifecycle/fulfillment status (defaults to PROCESSING and PARTIALLY_FULFILLED when omitted)",
                example = "PROCESSING",
                nullable = true
        )
        OrderStatus status,

        @Schema(
                description = "Filter by latest payment transaction status",
                example = "SUCCESS",
                nullable = true
        )
        PaymentStatus paymentStatus,

        @Schema(
                description = "Filter by shipping delivery status",
                example = "PENDING",
                nullable = true
        )
        DeliveryStatus deliveryStatus,

        @Schema(description = "Zero-based page index (0 to 1000000)", example = "0", defaultValue = "0")
        @Min(value = 0, message = "page must be greater than or equal to 0")
        @Max(value = 1000000, message = "page must not exceed 1000000")
        Integer page,

        @Schema(description = "Page size (1 to 50)", example = "10", defaultValue = "10")
        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 50, message = "size must not exceed 50")
        Integer size
) {
    public StaffOrderFilterRequest {
        page = (page != null) ? page : 0;
        size = (size != null) ? size : 10;
    }

    public StaffOrderFilterRequest() {
        this(null, null, null, 0, 10);
    }
}
