package com.lumintra.backend.device.api.request;

import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Query parameters for filtering and searching customer warranty claims across the system")
public record WarrantyClaimFilterRequest(
        @Schema(
                description = "Filter by warranty claim status",
                example = "PENDING",
                nullable = true
        )
        WarrantyTicketStatus status,

        @Schema(
                description = "Keyword search matching device serial number, customer name, email, phone, or issue description",
                example = "LUM-6432",
                nullable = true
        )
        String query,

        @Schema(description = "Zero-based page index (0 to 1000000)", example = "0", defaultValue = "0")
        @Min(value = 0, message = "page must be greater than or equal to 0")
        @Max(value = 1000000, message = "page must not exceed 1000000")
        Integer page,

        @Schema(description = "Page size (1 to 100)", example = "10", defaultValue = "10")
        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 100, message = "size must not exceed 100")
        Integer size
) {
    public WarrantyClaimFilterRequest {
        page = (page != null) ? page : 0;
        size = (size != null) ? size : 10;
        query = (query != null && !query.isBlank()) ? query.trim() : null;
    }

    public WarrantyClaimFilterRequest() {
        this(null, null, 0, 10);
    }
}
