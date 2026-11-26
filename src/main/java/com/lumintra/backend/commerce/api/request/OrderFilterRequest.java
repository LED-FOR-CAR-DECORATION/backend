package com.lumintra.backend.commerce.api.request;

import com.lumintra.backend.commerce.api.request.validation.ValidOrderDateRange;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@ValidOrderDateRange
@Schema(description = "Query parameters for listing and filtering the authenticated customer's order history")
public record OrderFilterRequest(
        @Schema(description = "Filter by exact order lifecycle status", example = "PENDING_PAYMENT", nullable = true)
        OrderStatus status,

        @Schema(description = "Filter orders created on or after this ISO-8601 date (UTC)", example = "2026-11-01", nullable = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Schema(description = "Filter orders created on or before this ISO-8601 date (UTC)", example = "2026-11-30", nullable = true)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate,

        @Schema(description = "Zero-based page index (min 0)", example = "0", defaultValue = "0")
        @Min(value = 0, message = "page must be greater than or equal to 0")
        Integer page,

        @Schema(description = "Page size (1 to 50)", example = "10", defaultValue = "10")
        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 50, message = "size must not exceed 50")
        Integer size
) {
    public OrderFilterRequest {
        page = (page != null) ? page : 0;
        size = (size != null) ? size : 10;
    }

    public OrderFilterRequest() {
        this(null, null, null, 0, 10);
    }

    public OrderFilterRequest(OrderStatus status, LocalDate fromDate, LocalDate toDate) {
        this(status, fromDate, toDate, 0, 10);
    }

    public OrderFilterRequest(Integer page, Integer size) {
        this(null, null, null, page, size);
    }
}
